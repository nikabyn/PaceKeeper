package org.htwk.pacing.ui.components

import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.HeartRateRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Testdaten für Import:
 * http://uni.pixelpioniere.de/Softwareprojekt/HR4.csv
 */

@Composable
fun ImportDataHealthConnect() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<android.net.Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = {
            uri = it
            name = it?.lastPathSegment ?: ""
        }
    )

    Column(
        modifier = Modifier
            .padding(top=16.dp)
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
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
                    val selected = uri ?: run {
                        status = "Keine Datei ausgewählt"
                        return@launch
                    }
                    try {
                        val stream = context.contentResolver.openInputStream(selected)
                            ?: throw Exception("Datei nicht lesbar")
                        val reader = BufferedReader(InputStreamReader(stream))
                        val client = HealthConnectClient.getOrCreate(context)
                        val batch = mutableListOf<HeartRateRecord>()
                        var total = 0

                        val lines = reader.readLines()



                        val batchSize = 500


                        lines.drop(1).forEach { line ->
                            val parts = line.split(",")
                            if (parts.size < 5) return@forEach
                            try {
                                val date = parts[2].trim()
                                val time = parts[3].trim().replace("\"", "")
                                val bpm = parts[4].trim().toLong()
                                val ts = try {
                                    ZonedDateTime.parse("${date}T$time")
                                } catch (_: Exception) {
                                    LocalDateTime.parse("${date}T$time").atZone(ZoneId.systemDefault())
                                }
                                batch.add(
                                    HeartRateRecord(
                                        startTime = ts.toInstant(),
                                        startZoneOffset = ts.offset,
                                        endTime = ts.toInstant(),
                                        endZoneOffset = ts.offset,
                                        samples = listOf(
                                            HeartRateRecord.Sample(ts.toInstant(), bpm)
                                        )
                                    )
                                )
                                if (batch.size >= batchSize) {
                                    client.insertRecords(batch.toList())
                                    total += batch.size
                                    batch.clear()
                                }
                            } catch (e: Exception) {
                                Log.e("CSV", "Zeile fehlerhaft: ${e.message}")
                            }
                        }

                        if (batch.isNotEmpty()) {
                            for (record in batch) {
                                try {
                                    client.insertRecords(listOf(record))
                                    Log.i("HealthInsert", "OK: ${record}")
                                } catch (e: Exception) {
                                    Log.e("HealthInsert", "Fehler bei Record: ${record} => ${e.message}")
                                }
                            }
                            total += batch.size
                        }

                        status = "Import: $total Werte"
                    } catch (e: Exception) {
                        status = "Fehler: ${e.localizedMessage}"
                    }
                }
            },
            enabled = uri != null
        ) {
            Text("Import starten")
        }
        if (status.isNotEmpty()) Text(status, Modifier.padding(top = 12.dp))
    }
}
