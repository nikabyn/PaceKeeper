package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.StepsEntry
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object DataGenerateJob {

    const val TAG = "DataGenerateJob"
    private val firstImport = 30.days
    private val generationInterval = 1.minutes

    suspend fun run(context: Context, db: PacingDatabase) = coroutineScope {
        Log.i(TAG, "DataGenerateJob gestartet")

        resetDb(db)

        val exportDir = context.getExternalFilesDir("pacing_export") ?: return@coroutineScope
        val path = exportDir.absolutePath

        // Initial import: 30 Tage
        val now = Clock.System.now()
        importDemoData(
            db,
            path,
            from = now - firstImport,
            to = now
        )

        // Sliding Window: alle 10 Minuten
        while (true) {
            try {
                Log.d("Test123", "Generiere neue Daten...")

                performDataGeneration(db, path)
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei Datengenerierung", e)
            }
            delay(generationInterval)
        }
    }

    private suspend fun resetDb(db: PacingDatabase) {
        db.heartRateDao().deleteAll()
        db.sleepSessionsDao().deleteAll()
        db.stepsDao().deleteAll()
        db.predictedEnergyLevelDao().deleteAll()
        db.distanceDao().deleteAll()
        Log.i(TAG, "Datenbank wurde zurückgesetzt")
    }

    fun readCsv(filePath: String): List<Map<String, String>> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()

        val reader = file.bufferedReader()
        val header = reader.readLine()?.split(",") ?: return emptyList()

        return reader.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val values = line.split(",")
                header.zip(values).toMap()
            }
            .toList()
    }

    fun importDemoData(
        db: PacingDatabase,
        path: String,
        from: Instant,
        to: Instant
    ) {
        val heartRateCsv = readCsv("$path/heart_rate.csv")
        if (heartRateCsv.isEmpty()) return

        val predictedCsv = readCsv("$path/predicted_energy_level.csv")
        val stepsCsv = readCsv("$path/steps.csv")
        val distanceCsv = readCsv("$path/distance.csv")
        val sleepCsv = readCsv("$path/sleep-sessions.csv")

        val csvStartTime = Instant.parse(heartRateCsv.first()["timestamp"]!!)
        val offset = from - csvStartTime

        Log.d(TAG, "Importiere Daten von $from bis $to (offset=$offset)")

        // HeartRate
        val heartRates = heartRateCsv.mapNotNull { row ->
            val csvTime = Instant.parse(row["timestamp"]!!)
            val time = csvTime + offset
            if (time !in from..<to) return@mapNotNull null

            row["bpm"]?.toIntOrNull()?.let { bpm ->
                HeartRateEntry(time, bpm.toLong())
            }
        }
        db.heartRateDao().insertMany(heartRates)

        // Predicted Energy Level
        val predictedEnergy = predictedCsv.mapNotNull { row ->
            try {
                val csvTime = Instant.parse(row["timestamp"]!!)
                val time = csvTime + offset
                if (time !in from..<to) return@mapNotNull null

                val percentageNow =
                    row["percentageNow"]!!.removeSuffix("%").toDouble() / 100.0
                val percentageFuture =
                    row["percentageFuture"]!!.removeSuffix("%").toDouble() / 100.0

                PredictedEnergyLevelEntry(
                    time = time,
                    percentageNow = Percentage.fromDouble(
                        BigDecimal(percentageNow).setScale(2, RoundingMode.HALF_UP).toDouble()
                    ),
                    timeFuture = Instant.parse(row["timeFuture"]!!) + offset,
                    percentageFuture = Percentage.fromDouble(
                        BigDecimal(percentageFuture).setScale(2, RoundingMode.HALF_UP).toDouble()
                    )
                )
            } catch (_: Exception) {
                null
            }
        }
        db.predictedEnergyLevelDao().insertMany(predictedEnergy)

        // Steps
        val steps = stepsCsv.mapNotNull { row ->
            val start = Instant.parse(row["timestamp"]!!) + offset
            if (start < from || start >= to) return@mapNotNull null

            val end = Instant.fromEpochMilliseconds(row["end"]!!.toLong()) + offset
            val count = row["count"]!!.toLong()

            StepsEntry(start, end, count)
        }
        db.stepsDao().insertMany(steps)

        // Distance
        val distances = distanceCsv.mapNotNull { row ->
            val time = Instant.parse(row["timestamp"]!!) + offset
            if (time !in from..<to) return@mapNotNull null

            DistanceEntry(
                start = time,
                end = time,
                length = Length.meters(row["distanceMeters"]!!.toDouble())
            )
        }
        db.distanceDao().insertMany(distances)

        // Sleep Sessions
        val sleeps = sleepCsv.mapNotNull { row ->
            val start = Instant.parse(row["timestamp"]!!) + offset
            if (start !in from..<to) return@mapNotNull null

            val end = Instant.fromEpochMilliseconds(row["end"]!!.toLong()) + offset
            val stage = SleepStage.fromString(row["stage"]!!)

            SleepSessionEntry(start, end, stage)
        }
        db.sleepSessionsDao().insertMany(sleeps)
    }

    private suspend fun performDataGeneration(
        db: PacingDatabase,
        path: String
    ) {
        val latest =
            db.heartRateDao().getLatestTimestamp() ?: return

        val to = latest + generationInterval

        Log.d(TAG, "Live-Import von $latest bis $to")

        importDemoData(db, path, latest, to)
    }
}