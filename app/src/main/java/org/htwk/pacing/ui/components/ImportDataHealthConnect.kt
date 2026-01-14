package org.htwk.pacing.ui.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.metadata.Metadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.htwk.pacing.R
import org.htwk.pacing.backend.data_collection.health_connect.HealthConnectHelper.insertHeartRateRecords
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Testdaten für Import:
 * Beispieldaten unter samples/health_connect/hr4.csv
 */
@Composable
fun ImportDataHealthConnect() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val msgSelectCSVFile = context.getString(R.string.select_csv_file)

    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { resultUri ->
            if (resultUri != null) {
                val fileName = getFileName(context, resultUri)
                if (!fileName.endsWith(".csv", ignoreCase = true)) {
                    status = msgSelectCSVFile
                    uri = null
                    name = ""
                } else {
                    uri = resultUri
                    name = fileName
                    status = ""
                }
            }
        }
    )

    Card(
        colors = CardStyle.colors,
        shape = CardStyle.shape,
    ) {
        Column(
            modifier = Modifier
                .padding(
                    horizontal = Spacing.large,
                    vertical = Spacing.largeIncreased
                )
                .fillMaxWidth()
        ) {
            Text(
                stringResource(R.string.import_heart_rate_data_csv),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(arrayOf("text/*")) },
                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                style = PrimaryButtonStyle) {
                Text(stringResource(R.string.select_file))
            }
            if (name.isNotEmpty()) Text(name, Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        status = importHeartRateData(context, uri)
                    }
                },
                style = PrimaryButtonStyle,
                enabled = uri != null
            ) {
                Text(stringResource(R.string.import_start))
            }
            if (status.isNotEmpty()) Text(status, Modifier.padding(top = 12.dp))
        }
    }
}

suspend fun importHeartRateData(context: Context, uri: Uri?): String {
    if (uri == null) return context.getString(R.string.no_file_selected)

    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return context.getString(R.string.file_not_readable)
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines().drop(1)

        val records = parseHeartRateRecords(lines)
        val total = insertHeartRateRecords(context, records)

        context.getString(R.string.import_success, total)
    } catch (e: IOException) {
        context.getString(R.string.error_reading, e.localizedMessage)
    } catch (e: Exception) {
        context.getString(R.string.unknown_error, e.localizedMessage)
    }
}


fun parseHeartRateRecords(lines: List<String>): List<HeartRateRecord> {
    return lines.mapNotNull { line ->
        val parts = line.split(",")
        if (parts.size < 5) return@mapNotNull null

        try {
            val date = parts[2].trim()
            val time = parts[3].trim().replace("\"", "")
            val bpm = parts[4].trim().toLong()

            val ts = try {
                ZonedDateTime.parse("${date}T$time")
            } catch (_: Exception) {
                LocalDateTime.parse("${date}T$time").atZone(ZoneId.systemDefault())
            }

            HeartRateRecord(
                startTime = ts.toInstant(),
                startZoneOffset = ts.offset,
                endTime = ts.toInstant(),
                endZoneOffset = ts.offset,
                samples = listOf(HeartRateRecord.Sample(ts.toInstant(), bpm)),
                metadata = Metadata.unknownRecordingMethod(),
            )
        } catch (_: Exception) {
            null
        }
    }
}

fun getFileName(context: Context, uri: Uri): String {
    var name = ""
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) name = it.getString(index)
        }
    }
    return name
}
