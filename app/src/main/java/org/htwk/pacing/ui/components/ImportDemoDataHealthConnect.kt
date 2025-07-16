package org.htwk.pacing.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.health.connect.client.records.HeartRateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.htwk.pacing.R
import org.htwk.pacing.backend.data_collection.HealthConnectHelper.insertHeartRateRecords
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.*

@Composable
fun ImportDemoDataHealthConnect() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    Button(
        onClick = { showDialog = true },
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.demo_data_button_text))
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.demo_data_dialog_title)) },
            text = {
                Text(stringResource(R.string.demo_data_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        scope.launch {
                            val result = ImportDemoDataHealthConnectImpl().run(context)
                            Toast
                                .makeText(context, result, Toast.LENGTH_LONG)
                                .show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.agree))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

class ImportDemoDataHealthConnectImpl {

    suspend fun run(context: android.content.Context): String = withContext(Dispatchers.IO) {
        try {
            val records = parseDemoCsv(context)
            val inserted = insertHeartRateRecords(context, records)
            context.getString(R.string.import_success, inserted)
        } catch (e: Exception) {
            Log.e("DemoImport", "Fehler: ${e.message}", e)
            context.getString(R.string.unknown_error, e.localizedMessage ?: "")
        }
    }

    private fun parseDemoCsv(context: android.content.Context): List<HeartRateRecord> {
        val zone = ZoneId.systemDefault()
        val input = context.resources.openRawResource(R.raw.hr_demo)
        val reader = BufferedReader(InputStreamReader(input))

        return reader.lineSequence()
            .drop(1)
            .mapNotNull { line ->
                val parts = line.split(',')
                if (parts.size < 3) return@mapNotNull null

                val timeStr = parts[1].trim()
                val bpmStr = parts[2].trim()

                try {
                    val localTime = LocalTime.parse(timeStr)
                    val bpm = bpmStr.toLong()

                    val timestamp = mapTimeToTodayOrYesterday(localTime, zone)

                    HeartRateRecord(
                        startTime = timestamp.toInstant(),
                        startZoneOffset = timestamp.offset,
                        endTime = timestamp.toInstant(),
                        endZoneOffset = timestamp.offset,
                        samples = listOf(
                            HeartRateRecord.Sample(timestamp.toInstant(), bpm)
                        )
                    )
                } catch (e: Exception) {
                    Log.w("DemoImport", "Fehler beim Parsen: ${e.message}")
                    null
                }
            }
            .toList()
    }

    private fun mapTimeToTodayOrYesterday(localTime: LocalTime, zone: ZoneId): ZonedDateTime {
        val now = ZonedDateTime.now(zone)
        val today = now.toLocalDate()
        val yesterday = today.minusDays(1)

        val date = if (localTime.isAfter(now.toLocalTime())) yesterday else today
        return ZonedDateTime.of(date, localTime, zone)
    }
}
