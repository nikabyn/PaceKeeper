package org.htwk.pacing.ui.components

import android.net.Uri
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing
import java.util.zip.ZipInputStream

@Composable
fun ZipDataImport_import_temp(
    heartRateDao: HeartRateDao,
    validatedEnergyLevelDao: ValidatedEnergyLevelDao
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var uri by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { resultUri ->
            if (resultUri != null) {
                uri = resultUri
                name = resultUri.path?.substringAfterLast("/") ?: "Selected File"
                status = ""
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
                "Integrierter Import (ZIP)",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Importiert HR und Energy Level. Ignoriert predicted_energy_level.csv.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    launcher.launch(
                        arrayOf(
                            "application/zip",
                            "application/x-zip-compressed"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                style = PrimaryButtonStyle
            ) {
                Text(stringResource(R.string.select_file))
            }
            if (name.isNotEmpty()) Text(name, Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    if (uri != null && !isImporting) {
                        isImporting = true
                        status = "Importing..."
                        scope.launch(Dispatchers.IO) {
                            val resultMsg = try {
                                importZipData(context, uri!!, heartRateDao, validatedEnergyLevelDao)
                            } catch (e: Exception) {
                                Log.e("ZipImport", "Error", e)
                                "Error: ${e.message}"
                            }
                            status = resultMsg
                            isImporting = false
                        }
                    }
                },
                style = PrimaryButtonStyle,
                enabled = uri != null && !isImporting
            ) {
                Text(if (isImporting) "Import läuft..." else stringResource(R.string.import_start))
            }
            if (status.isNotEmpty()) Text(status, Modifier.padding(top = 12.dp))
        }
    }
}

private suspend fun importZipData(
    context: android.content.Context,
    uri: Uri,
    hrDao: HeartRateDao,
    energyDao: ValidatedEnergyLevelDao
): String {
    val contentResolver = context.contentResolver

    val hrList = mutableListOf<TempHeartRate>()
    val energyList = mutableListOf<TempEnergy>()

    // 1. Read ZIP
    contentResolver.openInputStream(uri)?.use { inputStream ->
        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val filename = entry.name.lowercase()

                    // CRITICAL FIX: Ignore the prediction file, otherwise it might be parsed as validation
                    if (filename.contains("predicted_energy")) {
                        zipStream.closeEntry()
                        entry = zipStream.nextEntry
                        continue
                    }

                    val bytes = zipStream.readBytes()
                    val text = String(bytes)

                    // Strict check for validated energy level file
                    if (filename.contains("validated_energy_level")) {
                        val lines = text.lines()
                        val parsed = parseEnergyCSV(lines)
                        if (parsed.isNotEmpty()) {
                            energyList.addAll(parsed)
                        }
                    } else if (filename.contains("heart_rate") && !filename.contains("variability")) {
                        val lines = text.lines()
                        hrList.addAll(parseHeartRateCSV(lines))
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }

    if (hrList.isEmpty() && energyList.isEmpty()) {
        return "Keine gültigen Daten gefunden (Prüfen Sie Dateinamen)."
    }

    // 2. Calculate Time Shift
    val maxHrTime = hrList.maxOfOrNull { it.timestamp }
    val maxEnergyTime = energyList.maxOfOrNull { it.timestamp }

    val maxSourceInstant = when {
        maxHrTime != null && maxEnergyTime != null -> if (maxHrTime > maxEnergyTime) maxHrTime else maxEnergyTime
        maxHrTime != null -> maxHrTime
        else -> maxEnergyTime!!
    }

    // Shift logic: Move the latest data point to "Today at Noon" to act as current data
    val timeZone = TimeZone.currentSystemDefault()
    val sourceDate = maxSourceInstant.toLocalDateTime(timeZone).date
    val todayDate = Clock.System.now().toLocalDateTime(timeZone).date

    val sourceNoon = kotlinx.datetime.LocalDateTime(
        sourceDate,
        kotlinx.datetime.LocalTime(12, 0)
    ).toInstant(timeZone)
    val todayNoon = kotlinx.datetime.LocalDateTime(
        todayDate,
        kotlinx.datetime.LocalTime(12, 0)
    ).toInstant(timeZone)

    val offset = todayNoon - sourceNoon

    // 3. Shift and Insert
    var countHr = 0
    var countEnergy = 0

    val newHrEntries = hrList.map {
        HeartRateEntry(
            time = it.timestamp + offset,
            bpm = it.bpm
        )
    }

    val newEnergyEntries = energyList.map {
        ValidatedEnergyLevelEntry(
            time = it.timestamp + offset,
            validation = it.validation,
            percentage = it.percentage
        )
    }

    newHrEntries.forEach { hrDao.insert(it); countHr++ }
    newEnergyEntries.forEach { energyDao.insert(it); countEnergy++ }

    return "Import erfolgreich. Zeitverschiebung: ${offset.inWholeDays} Tage.\nHR: $countHr, Energy: $countEnergy"
}

// ---- Parsers ----

private data class TempHeartRate(val timestamp: Instant, val bpm: Long)
private data class TempEnergy(
    val timestamp: Instant,
    val percentage: Percentage,
    val validation: Validation
)

private fun parseHeartRateCSV(lines: List<String>): List<TempHeartRate> {
    return lines.mapNotNull { line ->
        val parts = line.split(",")
        if (parts.size < 2) return@mapNotNull null

        try {
            val tsString = parts[0].trim()
            val bpmString = parts[1].trim()

            if (tsString.lowercase().contains("timestamp")) return@mapNotNull null

            val timestamp = Instant.parse(tsString)
            val bpm = bpmString.toDouble().toLong()

            TempHeartRate(timestamp, bpm)
        } catch (e: Exception) {
            null
        }
    }
}

private fun parseEnergyCSV(lines: List<String>): List<TempEnergy> {
    return lines.mapNotNull { line ->
        // FIXED FORMAT: timestamp, validation, percentage
        // Example: 2025-12-19T10:28:18.059Z, Correct, 47.22656394845787%
        val parts = line.split(",")
        if (parts.size < 3) return@mapNotNull null

        try {
            val tsString = parts[0].trim()
            if (tsString.lowercase().contains("timestamp")) return@mapNotNull null

            val timestamp = Instant.parse(tsString)

            // Index 1 is Validation
            val validationStr = parts[1].trim()

            // Index 2 is Percentage
            var pctString = parts[2].trim()
            if (pctString.endsWith("%")) pctString = pctString.dropLast(1)
            val pctVal = pctString.toDouble() / 100.0

            val percentage = Percentage(pctVal.coerceIn(0.0, 1.0))

            val validation = if (validationStr.equals(
                    "Adjusted",
                    ignoreCase = true
                )
            ) Validation.Adjusted else Validation.Correct

            TempEnergy(timestamp, percentage, validation)
        } catch (e: Exception) {
            Log.e("Import", "Failed parsing energy line: $line", e)
            null
        }
    }
}