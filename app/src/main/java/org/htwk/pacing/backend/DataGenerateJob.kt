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

        val predictedCsv = readCsv("$path/predicted_energy_level.csv")
        val stepsCsv = readCsv("$path/steps.csv")
        val distanceCsv = readCsv("$path/distance.csv")
        val sleepCsv = readCsv("$path/sleep-sessions.csv")
        val heartRateCsv = readCsv("$path/heart_rate.csv")

        val csvFiles = mapOf(
            "heartRate" to heartRateCsv,
            "predicted" to predictedCsv,
            "steps" to stepsCsv,
            "distance" to distanceCsv,
            "sleep" to sleepCsv
        )

        if (csvFiles.values.all { it.isEmpty() }) return@coroutineScope

        val now = Clock.System.now()
        val csvStartTime = Instant.parse(csvFiles["heartRate"]?.first()["timestamp"]!!)

        val csvFrom = csvStartTime
        val csvTo = csvStartTime + firstImport
        csvOffset = (now - firstImport) - csvStartTime

        Log.i(TAG, "Initial-Import: csvFrom=$csvFrom csvTo=$csvTo Offset=$csvOffset")

        importDemoData(
            db = db,
            csvFrom = csvFrom,
            csvTo = csvTo,
            offset = csvOffset!!,
            importPredictedEnergy = true,
            csvFiles = csvFiles
        )

        csvCursorTime = csvTo


        Log.i(TAG, "CSV-Start=$csvStartTime  Offset=$csvOffset")

        while (true) {
            try {
                Log.d("PerformDataGenration", "Generiere neue Daten...")

                performDataGeneration(db, csvFiles)
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
        csvFrom: Instant,
        csvTo: Instant,
        offset: kotlin.time.Duration,
        importPredictedEnergy: Boolean,
        csvFiles: Map<String, List<Map<String, String>>>
    ) {
        Log.d(TAG, "CSV-Import: csvFrom=$csvFrom csvTo=$csvTo offset=$offset")

        val heartRateCsv = csvFiles["heartRate"] ?: emptyList()
        val predictedCsv = csvFiles["predicted"] ?: emptyList()
        val stepsCsv = csvFiles["steps"] ?: emptyList()
        val distanceCsv = csvFiles["distance"] ?: emptyList()
        val sleepCsv = csvFiles["sleep"] ?: emptyList()

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

    private fun performDataGeneration(
        db: PacingDatabase,
        csvFiles: Map<String, List<Map<String, String>>>
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
            csvFrom = cursor,
            csvTo = nextCursor,
            offset = offset,
            importPredictedEnergy = false,
            csvFiles = csvFiles

        )

        csvCursorTime = nextCursor
    }
}