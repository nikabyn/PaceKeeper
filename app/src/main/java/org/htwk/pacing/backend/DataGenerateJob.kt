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
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object DataGenerateJob {
    const val TAG = "DataGenerateJob"

    private val generationInterval = 10.minutes

    suspend fun run(context: Context, db: PacingDatabase) = coroutineScope {
        Log.i(TAG, "DataGenerateJob gestartet")

        // App-internen externen Speicher nutzen
        val exportDir = context.getExternalFilesDir("pacing_export") ?: return@coroutineScope
        val path = exportDir.absolutePath
        importDemoData(db, path)

        while (true) {
            try {
                Log.d(TAG, "Generiere neue Daten...")
                performDataGeneration(db)
                //latestTimePoint = zeitpunkt des letzten importierten eintrags
            } catch (e: Exception) {
                Log.e(TAG, "Fehler: ${e.message}")
            }

            delay(generationInterval)
        }
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
            }.toList()
    }

    suspend fun importDemoData(db: PacingDatabase, path: String) {
        val now = Clock.System.now()
        val startTime = now - 30.days

        // HeartRate.csv als Startpunkt
        val heartRateCsv = readCsv("$path/heart_rate.csv")
        if (heartRateCsv.isEmpty()) return

        val csvStartTime = Instant.parse(heartRateCsv.first()["timestamp"]!!)
        val offset = startTime - csvStartTime

        // HeartRate-Einträge
        val heartRates = heartRateCsv.mapNotNull { row ->
            val time = Instant.parse(row["timestamp"]!!) + offset
            row["bpm"]?.toIntOrNull()?.let { bpm ->
                HeartRateEntry(time, bpm.toLong())
            }
        }
        db.heartRateDao().insertMany(heartRates)

        // Predicted_Energy_Level
        val predictedCsv = readCsv("$path/predicted_energy_level.csv")
        val predictedEnergy = predictedCsv.mapNotNull { row ->
            try {
                val time = Instant.parse(row["timestamp"]!!) + offset
                val percentageNow = row["percentageNow"]?.removeSuffix("%")?.toDoubleOrNull()
                    ?: return@mapNotNull null
                val timeFuture = Instant.parse(row["timeFuture"]!!) + offset
                val percentageFuture = row["percentageFuture"]?.removeSuffix("%")?.toDoubleOrNull()
                    ?: return@mapNotNull null

                PredictedEnergyLevelEntry(
                    time = time,
                    percentageNow = Percentage.fromDouble((percentageNow) / 100),
                    timeFuture = timeFuture,
                    percentageFuture = Percentage.fromDouble((percentageFuture) / 100)
                )
            } catch (e: Exception) {
                null
            }
        }
        db.predictedEnergyLevelDao().insertMany(predictedEnergy)

        // steps.csv
        val stepsCsv = readCsv("$path/steps.csv")
        val steps = stepsCsv.mapNotNull { row ->
            val start = Instant.parse(row["timestamp"]!!) + offset
            val endMillis = row["end"]?.toLongOrNull() ?: return@mapNotNull null
            val end = Instant.fromEpochMilliseconds(endMillis) + offset
            row["count"]?.toIntOrNull()?.let { count ->
                StepsEntry(start, end, count.toLong())
            }
        }
        db.stepsDao().insertMany(steps)

        // distance.csv
        val distanceCsv = readCsv("$path/distance.csv")
        val distances = distanceCsv.mapNotNull { row ->
            val start = Instant.parse(row["timestamp"]!!) + offset
            row["distanceMeters"]?.toDoubleOrNull()?.let { d ->
                DistanceEntry(start, start, Length.meters(d))
            }
        }
        db.distanceDao().insertMany(distances)

        // sleep-sessions.csv
        val sleepCsv = readCsv("$path/sleep-sessions.csv")
        val sleeps = sleepCsv.mapNotNull { row ->
            val start = Instant.parse(row["timestamp"]!!) + offset
            val endMillis = row["end"]?.toLongOrNull() ?: return@mapNotNull null
            val end = Instant.fromEpochMilliseconds(endMillis) + offset
            val stage = row["stage"]?.let { SleepStage.fromString(it) } ?: return@mapNotNull null
            SleepSessionEntry(start, end, stage)
        }
        db.sleepSessionsDao().insertMany(sleeps)
    }

    private fun performDataGeneration(db: PacingDatabase) {
        val now = Clock.System.now()
        //muss äuivalenten Zeitpunnkt von der csv speichern, damit man von dem aus 10 min weiter importieren kann
        // fenster weiter schieben --> anfang csv datei ==> einen monat reinladen. dann ein 10 min fenster immmer weiter schieben
        // erster eintrag aus csv vor einem monat 6 uhr am morgen
        // von dort aus 30 tage reinladen
        // dann alle 10 min weiter auffühlen
        // wichtig: HearRate, Energielevel, Energielevel_validatet, symptome

        //bei storeRecords sieht man wie in die datenbank geschrieben werden kann

    }

}