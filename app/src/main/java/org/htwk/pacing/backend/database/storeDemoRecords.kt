package org.htwk.pacing.backend.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import java.io.InputStream
import java.util.zip.ZipInputStream

suspend fun storeDemoRecords(db: PacingDatabase, zipStream: InputStream) =
    withContext(Dispatchers.IO) {
        ZipInputStream(zipStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val rows = zis.bufferedReader().useLines { lines ->
                    lines.drop(1).map { it.split(",") }.toList() // Header überspringen
                }

                when (entry.name.lowercase()) {
                    "steps.csv" -> db.stepsDao().insertMany(
                        rows.map {
                            StepsEntry(
                                start = Instant.parse(it[0]),
                                end = Instant.parse(it[1]),
                                count = it[2].toLong()
                            )
                        }
                    )

                    "heart_rate.csv" -> db.heartRateDao().insertMany(
                        rows.map {
                            HeartRateEntry(
                                time = Instant.parse(it[0]),
                                bpm = it[1].toLong()
                            )
                        }
                    )

                    "distance.csv" -> db.distanceDao().insertMany(
                        rows.map {
                            DistanceEntry(
                                start = Instant.parse(it[0]),
                                end = Instant.parse(it[0]),
                                length = Length.meters(it[1].toDouble())
                            )
                        }
                    )

                    "elevation.csv" -> db.elevationGainedDao().insertMany(
                        rows.map {
                            ElevationGainedEntry(
                                start = Instant.parse(it[0]),
                                end = Instant.parse(it[0]),
                                length = Length.meters(it[1].toDouble())
                            )
                        }
                    )

                    // weitere CSVs hier analog hinzufügen
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
