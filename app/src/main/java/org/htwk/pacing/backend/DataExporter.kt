package org.htwk.pacing.backend

import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class DataExporter {

    companion object {
        private const val CSV_HEADER = "Datum,Aktivität,Dauer (Minuten),Kalorien,Herzfrequenz"
        private const val DATE_FORMAT = "yyyy-MM-dd HH:mm:ss"
    }

    fun exportToCsv(fitnessData: List<FitnessData>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.write("$CSV_HEADER\n")
            fitnessData.forEach { data ->
                writer.write(buildCsvLine(data))
            }
        }
    }

    //Hinzufügen der entsprechenden Daten
    private fun buildCsvLine(data: FitnessData): String {
        return buildString {
            append(formatDateTime(data.timestamp))
            append(",")
            append(escapeField(data.activityType))
            append(",")
            append(data.durationMinutes)
            append(",")
            append(data.calories)
            append(",")
            append(data.heartRate)
            append("\n")
        }
    }

    private fun escapeField(field: String): String {
        return "\"${field.replace("\"", "\"\"")}\""
    }

    private fun formatDateTime(dateTime: LocalDateTime): String {
        return dateTime.format(DateTimeFormatter.ofPattern(DATE_FORMAT))
    }
}
