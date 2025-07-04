package org.htwk.pacing.ui.components

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = {
            uri = it
            name = it?.lastPathSegment ?: ""
        }
    )

    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text("Importiere Herzfrequenzdaten (.csv)", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { launcher.launch(arrayOf("text/csv")) }) {
            Text("Datei auswählen")
        }
        if (name.isNotEmpty()) Text(name, Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    status = importHeartRateData(context, uri)
                }
            },
            enabled = uri != null
        ) {
            Text("Import starten")
        }
        if (status.isNotEmpty()) Text(status, Modifier.padding(top = 12.dp))
    }
}

// ------------------------------------------------------------
// Importfunktion ausgelagert in saubere Methoden
// ------------------------------------------------------------

suspend fun importHeartRateData(context: Context, uri: Uri?): String {
    if (uri == null) return "Keine Datei ausgewählt"

    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: return "Datei nicht lesbar"
        val reader = BufferedReader(InputStreamReader(inputStream))
        val lines = reader.readLines().drop(1)

        val records = parseHeartRateRecords(lines)
        val total = insertHeartRateRecords(context, records)

        "Import: $total Werte"
    } catch (e: IOException) {
        "Fehler beim Lesen: ${e.localizedMessage}"
    } catch (e: Exception) {
        "Unbekannter Fehler: ${e.localizedMessage}"
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
                samples = listOf(
                    HeartRateRecord.Sample(ts.toInstant(), bpm)
                )
            )
        } catch (e: Exception) {
            Log.e("CSV", "Fehler beim Parsen: ${e.message}")
            null
        }
    }
}

suspend fun insertHeartRateRecords(context: Context, records: List<HeartRateRecord>): Int {
    val client = HealthConnectClient.getOrCreate(context)
    val batchSize = 500
    var totalInserted = 0

    records.chunked(batchSize).forEach { batch ->
        try {
            client.insertRecords(batch)
            totalInserted += batch.size
        } catch (e: Exception) {
            Log.e("HealthInsert", "Fehler beim Batch Insert: ${e.message}")
        }
    }

    return totalInserted
}
