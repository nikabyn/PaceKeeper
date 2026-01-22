package org.htwk.pacing.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.htwk.pacing.R
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing
import org.koin.androidx.compose.koinViewModel

@Composable
fun StartEvaluationMode(
    modeViewModel: ModeViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }
    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = Spacing.large,
                vertical = Spacing.largeIncreased
            )
        ) {
            Text(
                stringResource(R.string.demo_data_button_text),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { showDialog = true },
                style = PrimaryButtonStyle,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (modeViewModel.mode.collectAsState().value?.demo == true) (
                        Text(stringResource(R.string.demo_start_button_text)))
                else
                    (Text(stringResource(R.string.demo_end_button_text)))
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.demo_data_dialog_title)) },
            text = {
                Text(stringResource(R.string.evaluation_mode_dialog_text))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDialog = false
                        if (modeViewModel.mode.value?.demo == true) (
                                modeViewModel.setDemoMode(false)
                                )
                        else (
                                modeViewModel.setDemoMode(true)
                                )

                        Toast
                            .makeText(
                                context,
                                "Test",
                                Toast.LENGTH_LONG
                            )
                            .show()
                        //worker stoppen

                        //app killen

                        //CSV laden
                        modeViewModel.storeDemoRecords(
                            "/storage/emulated/0/Downloads/pacing_export (4).zip"
                        )
                        // exitApp(context)
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
/*
class DemoDataImpl {

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
                        samples = listOf(HeartRateRecord.Sample(timestamp.toInstant(), bpm)),
                        metadata = Metadata.unknownRecordingMethod(),
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
*/
/*
fun exitApp(context: Context) {
    // 1. Worker stoppen
    //   WorkManager.getInstance(context).cancelAllWork()
    //ForegroundWorker

    stopForegroundWorker(WorkManagerImpl(contgitext))

    // 3. (Optional) Prozess beenden
    // android.os.Process.killProcess(android.os.Process.myPid())
}
*/