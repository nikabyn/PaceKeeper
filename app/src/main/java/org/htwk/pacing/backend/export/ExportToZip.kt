package org.htwk.pacing.backend.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.htwk.pacing.backend.database.*
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

suspend fun exportAllAsZip(db: PacingDatabase, outputStream: OutputStream) = withContext(Dispatchers.IO) {
    ZipOutputStream(outputStream).use { zipOut ->

        exportEntityToCsv("heart_rate.csv", zipOut) {
            db.heartRateDao().getAll().map {
                CsvRow(it.time.toString(), mapOf("bpm" to it.bpm.toString()))
            }
        }

        exportEntityToCsv("distance.csv", zipOut) {
            db.distanceDao().getAll().map {
                CsvRow(it.start.toString(), mapOf("distanceMeters" to it.length.inMeters().toString()))
            }
        }

        exportEntityToCsv("elevation.csv", zipOut) {
            db.elevationGainedDao().getAll().map {
                CsvRow(it.start.toString(), mapOf("elevationMeters" to it.length.inMeters().toString()))
            }
        }

        exportEntityToCsv("energy_level.csv", zipOut) {
            db.validatedEnergyLevelDao().getAll().map {
                CsvRow(it.time.toString(), mapOf("energyLevel" to it.percentage.toString()))
            }
        }

        exportEntityToCsv("heart_rate_variability.csv", zipOut) {
            db.heartRateVariabilityDao().getAll().map {
                CsvRow(it.time.toString(), mapOf("heart_rate_variability" to it.variability.toString()))
            }
        }

        exportEntityToCsv("menstruation.csv", zipOut) {
            db.menstruationPeriodDao().getAll().map {
                CsvRow(it.start.toString(), mapOf("end" to it.end.toEpochMilliseconds().toString()))
            }
        }

        exportEntityToCsv("oxygen_saturation.csv", zipOut) {
            db.oxygenSaturationDao().getAll().map {
                CsvRow(it.time.toString(), mapOf("saturation" to it.percentage.toString()))
            }
        }

        exportEntityToCsv("skin_temperature.csv", zipOut) {
            db.skinTemperatureDao().getAll().map {
                CsvRow(it.time.toString(), mapOf("tempCelsius" to it.temperature.inCelsius().toString()))
            }
        }

        exportEntityToCsv("sleep_sessions.csv", zipOut) {
            db.sleepSessionsDao().getAll().map {
                CsvRow(it.start.toString(), mapOf(
                    "end" to it.end.toEpochMilliseconds().toString(),
                    "stage" to it.stage.toString()
                ))
            }
        }

        exportEntityToCsv("speed.csv", zipOut) {
            db.speedDao().getAll().map {
                CsvRow(it.time.toString(), mapOf("speed" to it.velocity.inMetersPerSecond().toString()))
            }
        }

        exportEntityToCsv("steps.csv", zipOut) {
            db.stepsDao().getAll().map {
                CsvRow(it.start.toString(), mapOf(
                    "end" to it.end.toEpochMilliseconds().toString(),
                    "count" to it.count.toString()
                ))
            }
        }
    }
}

private suspend fun exportEntityToCsv(
    filename: String,
    zipOut: ZipOutputStream,
    rowsProvider: suspend () -> List<CsvRow>
) {
    val rows = rowsProvider()
    zipOut.putNextEntry(ZipEntry(filename))

    rows.forEachIndexed { index, row ->
        val line = buildString {
            if (index == 0) {
                append("timestamp")
                row.data.keys.forEach { append(",$it") }
                append("\n")
            }
            append(row.timestamp)
            row.data.values.forEach { append(",$it") }
            append("\n")
        }
        zipOut.write(line.toByteArray())
    }

    zipOut.closeEntry()
}

private data class CsvRow(
    val timestamp: String,
    val data: Map<String, String>
)
