package org.htwk.pacing.data

import android.content.Context
import android.util.Log
import androidx.health.connect.client.records.HeartRateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.htwk.pacing.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.*
import org.htwk.pacing.backend.data_collection.HealthConnectHelper.insertHeartRateRecords

class ImportDemoDataHealthConnect {

    suspend fun run(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val records = parseDemoCsv(context)
            val inserted = insertHeartRateRecords(context, records)
            context.getString(R.string.import_success, inserted)
        } catch (e: Exception) {
            Log.e("DemoImport", "Fehler: ${e.message}", e)
            context.getString(R.string.unknown_error, e.localizedMessage ?: "")
        }
    }

    private fun parseDemoCsv(context: Context): List<HeartRateRecord> {
        val zone = ZoneId.systemDefault()
        val input = context.resources.openRawResource(R.raw.hr_demo)
        val reader = BufferedReader(InputStreamReader(input))

        return reader.lineSequence()
            .drop(1) // Header
            .mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size < 2) return@mapNotNull null

                val timeStr = parts[0].trim()
                val bpmStr = parts[1].trim()

                try {
                    val localTime = LocalTime.parse(timeStr)
                    val bpm = bpmStr.toLong()

                    val ts = mapTimeToTodayOrYesterday(localTime, zone)

                    HeartRateRecord(
                        startTime = ts.toInstant(),
                        startZoneOffset = ts.offset,
                        endTime = ts.toInstant(),
                        endZoneOffset = ts.offset,
                        samples = listOf(
                            HeartRateRecord.Sample(ts.toInstant(), bpm)
                        )
                    )
                } catch (_: Exception) {
                    null
                }
            }
            .toList()
    }

    private fun mapTimeToTodayOrYesterday(
        localTime: LocalTime,
        zone: ZoneId
    ): ZonedDateTime {
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)

        val date = if (localTime.isAfter(now.toLocalTime())) yesterday else today
        return ZonedDateTime.of(date, localTime, zone)
    }
}
