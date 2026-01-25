package org.htwk.pacing.backend

import android.content.Context
import android.util.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import java.util.zip.ZipInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object DataGenerateJob {
    const val TAG = "DataGenerateJob"

    private val generationInterval = 60.minutes

    suspend fun run(context: Context, db: PacingDatabase) = coroutineScope {
        Log.i(TAG, "DataGenerateJob gestartet")

        checkAndImportDemoData(context, db)

        while (true) {
            try {
                Log.d(TAG, "Generiere neue Daten...")
                performDataGeneration(db)
            } catch (e: Exception) {
                Log.e(TAG, "Fehler: ${e.message}")
            }

            delay(generationInterval)
        }
    }

    private suspend fun checkAndImportDemoData(context: Context, db: PacingDatabase) {
        val thirtyDaysAgo = Clock.System.now() - 30.days
        val hasRecentData = db.readEventDao().getAll().any { it.time > thirtyDaysAgo }

        if (!hasRecentData) {
            val inputStream = context.resources.openRawResource(R.raw.pacing_export)
            storeDemoRecords(db, inputStream)
        }
    }

    private fun performDataGeneration(db: PacingDatabase) {
        val now = Clock.System.now()
        // Deine Logik: Letzte Einträge holen -> Zeit auf 'now' setzen -> insertMany()
        // ...
    }

    /**
     * Liest eine ZIP-Datei vom InputStream ein und speichert die CSV-Daten in die Datenbank.
     * Der Zeitstempel wird so angepasst, dass die Daten 30 Tage vor dem aktuellen Zeitpunkt liegen.
     */
    fun storeDemoRecords(
        db: PacingDatabase,
        inputStream: java.io.InputStream
    ) {
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry

            while (entry != null) {
                val reader = zis.bufferedReader()
                val rows = reader.lineSequence()
                    .filter { it.isNotBlank() }
                    .drop(1) // Header überspringen
                    .map { it.split(",") }
                    .toList()

                if (rows.isNotEmpty()) {
                    val earliestTimestamp: Instant = rows.minOf { Instant.parse(it[0]) }
                    val now: Instant = Clock.System.now()
                    val offset: Duration = (now - earliestTimestamp) - 30.days

                    when (entry.name.lowercase().substringAfterLast("/")) {

                        "steps.csv" ->
                            db.stepsDao().insertMany(
                                rows.map {
                                    val startOriginal = Instant.parse(it[0])
                                    val endOriginal = Instant.parse(it[1])
                                    StepsEntry(
                                        start = startOriginal + offset,
                                        end = endOriginal + offset,
                                        count = it[2].toLong()
                                    )
                                }
                            )

                        "heartrate.csv" ->
                            db.heartRateDao().insertMany(
                                rows.map {
                                    HeartRateEntry(
                                        time = Instant.parse(it[0]) + offset,
                                        bpm = it[1].toLong()
                                    )
                                }
                            )

                        "distance.csv" ->
                            db.distanceDao().insertMany(
                                rows.map {
                                    val ts = Instant.parse(it[0]) + offset
                                    DistanceEntry(
                                        start = ts,
                                        end = ts,
                                        length = Length.meters(it[1].toDouble())
                                    )
                                }
                            )

                        "elevation.csv" ->
                            db.elevationGainedDao().insertMany(
                                rows.map {
                                    val ts = Instant.parse(it[0]) + offset
                                    ElevationGainedEntry(
                                        start = ts,
                                        end = ts,
                                        length = Length.meters(it[1].toDouble())
                                    )
                                }
                            )

                        "skintemperature.csv" ->
                            db.skinTemperatureDao().insertMany(
                                rows.map {
                                    SkinTemperatureEntry(
                                        time = Instant.parse(it[0]) + offset,
                                        temperature = Temperature.celsius(it[1].toDouble())
                                    )
                                }
                            )

                        "sleep_session.csv" ->
                            db.sleepSessionsDao().insertMany(
                                rows.map {
                                    SleepSessionEntry(
                                        start = Instant.parse(it[0]) + offset,
                                        end = Instant.fromEpochMilliseconds(it[1].toLong()) + offset,
                                        stage = SleepStage.valueOf(it[2].uppercase())
                                    )
                                }
                            )

                        "predicted_energy_level.csv" ->
                            db.predictedEnergyLevelDao().insertMany(
                                rows.map {
                                    PredictedEnergyLevelEntry(
                                        time = Instant.parse(it[0]) + offset,
                                        percentageNow = Percentage(it[1].toDouble()),
                                        timeFuture = Instant.parse(it[2]) + offset,
                                        percentageFuture = Percentage(it[3].toDouble())
                                    )
                                }
                            )

                        "validated_energy_level.csv" ->
                            db.validatedEnergyLevelDao().insertMany(
                                rows.map {
                                    ValidatedEnergyLevelEntry(
                                        time = Instant.parse(it[0]) + offset,
                                        percentage = Percentage(
                                            it[1].removeSuffix("%")
                                                .toDouble()
                                                .coerceIn(0.0, 100.0)
                                        ),
                                        validation = when (it[2].lowercase()) {
                                            "correct" -> Validation.Correct
                                            "adjusted" -> Validation.Adjusted
                                            else -> Validation.Correct
                                        }
                                    )
                                }
                            )
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}