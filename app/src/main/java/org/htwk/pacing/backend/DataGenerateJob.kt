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

    private var csvCursorTime: Instant? = null
    private var csvOffset: kotlin.time.Duration? = null

    const val TAG = "DataGenerateJob"
    private val firstImport = 30.days
    private val generationInterval = 1.minutes

    suspend fun run(context: Context, db: PacingDatabase) = coroutineScope {
        Log.i(TAG, "DataGenerateJob gestartet")

        resetDb(db)

        val exportDir = context.getExternalFilesDir("pacing_export") ?: return@coroutineScope
        val path = exportDir.absolutePath

        val heartRateCsv = readCsv("$path/heart_rate.csv")
        if (heartRateCsv.isEmpty()) return@coroutineScope

        val csvStartTime = Instant.parse(heartRateCsv.first()["timestamp"]!!)
        val now = Clock.System.now()
        csvCursorTime = csvStartTime
        csvOffset = (now - firstImport) - csvStartTime

        // Initialer Import (z. B. 30 Tage auf einmal)
        val initialTo = csvStartTime + firstImport

        Log.i(
            TAG,
            "Initial-Import: csvFrom=$csvStartTime csvTo=$initialTo"
        )

        importDemoData(
            db = db,
            path = path,
            csvFrom = csvStartTime,
            csvTo = initialTo,
            offset = csvOffset!!,
            importPredictedEnergy = true
        )

        csvCursorTime = initialTo


        Log.i(TAG, "CSV-Start=$csvStartTime  Offset=$csvOffset")

        while (true) {
            try {
                Log.d("PerformDataGenration", "Generiere neue Daten...")

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
        db.predictedEnergyLevelDao().deleteAll()
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
        csvFrom: Instant,
        csvTo: Instant,
        offset: kotlin.time.Duration,
        importPredictedEnergy: Boolean
    ) {
        Log.d(
            TAG,
            "CSV-Import: csvFrom=$csvFrom csvTo=$csvTo offset=$offset"
        )

        val heartRateCsv = readCsv("$path/heart_rate.csv")
        val predictedCsv = readCsv("$path/predicted_energy_level.csv")
        val stepsCsv = readCsv("$path/steps.csv")
        val distanceCsv = readCsv("$path/distance.csv")
        val sleepCsv = readCsv("$path/sleep-sessions.csv")


        val heartRates = heartRateCsv.mapNotNull { row ->
            val csvTime = Instant.parse(row["timestamp"]!!)
            if (csvTime !in csvFrom..<csvTo) return@mapNotNull null

            HeartRateEntry(
                time = csvTime + offset,
                bpm = row["bpm"]!!.toLong()
            )
        }

        heartRates.forEach {
            Log.d("Eintrag", "HeartRate importiert: time=${it.time}, bpm=${it.bpm}")
        }

        db.heartRateDao().insertMany(heartRates)


        if (importPredictedEnergy) {
            val predictedEnergy = predictedCsv.mapNotNull { row ->
                try {
                    val csvTime = Instant.parse(row["timestamp"]!!)
                    if (csvTime !in csvFrom..<csvTo) return@mapNotNull null

                    val percentageNow =
                        row["percentageNow"]!!.removeSuffix("%").toDouble() / 100.0
                    val percentageFuture =
                        row["percentageFuture"]!!.removeSuffix("%").toDouble() / 100.0

                    PredictedEnergyLevelEntry(
                        time = csvTime + offset,
                        percentageNow = Percentage.fromDouble(
                            BigDecimal(percentageNow)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble()
                        ),
                        timeFuture = Instant.parse(row["timeFuture"]!!) + offset,
                        percentageFuture = Percentage.fromDouble(
                            BigDecimal(percentageFuture)
                                .setScale(2, RoundingMode.HALF_UP)
                                .toDouble()
                        )
                    )
                } catch (_: Exception) {
                    null
                }
            }

            db.predictedEnergyLevelDao().insertMany(predictedEnergy)
        }


        val steps = stepsCsv.mapNotNull { row ->
            val csvStart = Instant.parse(row["timestamp"]!!)
            if (csvStart !in csvFrom..<csvTo) return@mapNotNull null

            val start = csvStart + offset
            val end = Instant.fromEpochMilliseconds(row["end"]!!.toLong()) + offset

            StepsEntry(
                start = start,
                end = end,
                count = row["count"]!!.toLong()
            )
        }

        db.stepsDao().insertMany(steps)


        val distances = distanceCsv.mapNotNull { row ->
            val csvTime = Instant.parse(row["timestamp"]!!)
            if (csvTime !in csvFrom..<csvTo) return@mapNotNull null

            val time = csvTime + offset

            DistanceEntry(
                start = time,
                end = time,
                length = Length.meters(row["distanceMeters"]!!.toDouble())
            )
        }

        db.distanceDao().insertMany(distances)


        val sleeps = sleepCsv.mapNotNull { row ->
            val csvStart = Instant.parse(row["timestamp"]!!)
            if (csvStart !in csvFrom..<csvTo) return@mapNotNull null

            val start = csvStart + offset
            val end = Instant.fromEpochMilliseconds(row["end"]!!.toLong()) + offset
            val stage = SleepStage.fromString(row["stage"]!!)

            SleepSessionEntry(
                start = start,
                end = end,
                stage = stage
            )
        }

        db.sleepSessionsDao().insertMany(sleeps)
    }

    private suspend fun performDataGeneration(
        db: PacingDatabase,
        path: String
    ) {
        val cursor = csvCursorTime ?: return
        val offset = csvOffset ?: return

        val nextCursor = cursor + generationInterval

        Log.d(
            TAG,
            "CSV-Window: csvFrom=$cursor csvTo=$nextCursor"
        )

        importDemoData(
            db = db,
            path = path,
            csvFrom = cursor,
            csvTo = nextCursor,
            offset = offset,
            importPredictedEnergy = false

        )

        csvCursorTime = nextCursor
    }
}