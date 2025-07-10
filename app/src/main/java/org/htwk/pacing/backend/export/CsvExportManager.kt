package org.htwk.pacing.backend.export

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.toJavaInstant
import org.htwk.pacing.backend.database.*
import java.io.OutputStream
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CsvExportManager(context: Context) {

    private val db = PacingDatabase.getInstance(context)

    // Format für das Datum in der CSV
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    // Hauptmethode, um alle Daten zu exportieren
    fun exportAllData(outputStream: OutputStream) = runBlocking {
        // Alle Daten abrufen
        val heartRates = db.heartRateDao().getAll().map { it.toExportData() }
        val distances = db.distanceDao().getAll().map { it.toExportData() }
        val elevations = db.elevationGainedDao().getAll().map { it.toExportData() }
        val energyLevels = db.energyLevelDao().getAll().map { it.toExportData() }
        val hrv = db.heartRateVariabilityDao().getAll().map { it.toExportData() }
        val menstruation = db.menstruationPeriodDao().getAll().map { it.toExportData() }
        val oxygenSaturation = db.oxygenSaturationDao().getAll().map { it.toExportData() }
        val skinTemps = db.skinTemperatureDao().getAll().map { it.toExportData() }
        val sleepSessions = db.sleepSessionsDao().getAll().map { it.toExportData() }
        val speeds = db.speedDao().getAll().map { it.toExportData() }
        val steps = db.stepsDao().getAll().map { it.toExportData() }

        // Alle Daten zusammenfassen
        val allData = (heartRates + distances + elevations + energyLevels + hrv + menstruation +
                oxygenSaturation + skinTemps + sleepSessions + speeds + steps)
            .groupBy { it.timestamp }  // gruppiere nach Timestamp
            .toSortedMap()             // sortiere nach Timestamp

        outputStream.bufferedWriter().use { writer ->
            // Header bauen: Zeitstempel + alle Spaltennamen, die in Values vorkommen
            val allKeys = allData.values.flatMap { list -> list.flatMap { it.values.keys } }.toSet()
            val header = listOf("Timestamp") + allKeys.sorted()
            writer.write(header.joinToString(","))
            writer.newLine()

            // Zeilen schreiben
            allData.forEach { (timestamp, entries) ->
                val rowMap = mutableMapOf<String, String>()
                entries.forEach { entry ->
                    rowMap.putAll(entry.values)
                }

                val row = buildList {
                    add(dateFormatter.format(timestamp.toJavaInstant()))
                    allKeys.sorted().forEach { key ->
                        add(rowMap[key] ?: "")
                    }
                }
                writer.write(row.joinToString(","))
                writer.newLine()
            }
        }
    }
}
