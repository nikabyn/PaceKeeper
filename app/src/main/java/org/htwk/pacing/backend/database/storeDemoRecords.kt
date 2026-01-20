package org.htwk.pacing.backend.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

suspend fun storeDemoRecords(
    db: PacingDatabase,
    zipStream: InputStream
) = withContext(Dispatchers.IO) {

    ZipInputStream(zipStream).use { zis ->
        var entry = zis.nextEntry

        while (entry != null) {

            // CSV vollständig lesen, Header überspringen
            val rows = zis.bufferedReader()
                .readLines()
                .drop(1)
                .map { it.split(",") }

            if (rows.isEmpty()) {
                zis.closeEntry()
                entry = zis.nextEntry
                continue
            }

            val earliestTimestamp: Instant = rows.minOf { Instant.parse(it[0]) }
            val now: Instant = Clock.System.now()
            val offset: Duration = (now - earliestTimestamp) - 30.days

            when (entry.name.lowercase()) {

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

                "validated_energy_kevek.csv" ->
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

            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}
