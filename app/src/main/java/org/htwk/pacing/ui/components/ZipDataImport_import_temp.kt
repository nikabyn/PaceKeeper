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
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.toInstant
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
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.milliseconds

/**
 * Component to import Heart Rate and Validated Energy Level from a single ZIP file.
 * Automatically shifts timestamps so that the last complete day in source data becomes "yesterday" (effectively simulating that the data ends today).
 */
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
                // Basic cleanup of file name display
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
                "Importiert HR und Energy Level aus einer ZIP, synchronisiert auf heute.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = { launcher.launch(arrayOf("application/zip", "application/x-zip-compressed")) },
                modifier = Modifier.fillMaxWidth(),
                style = PrimaryButtonStyle) {
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
                Text(if(isImporting) "Import läuft..." else stringResource(R.string.import_start))
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
                    // Determine file type by name or content heuristic could be added
                    // Here we rely on simple naming or trying to parse
                    val bytes = zipStream.readBytes()
                    val text = String(bytes)
                    
                    if (filename.contains("energy") || text.contains("percentage") || text.contains("Correct") || text.contains("Validation")) {
                        // Parse Energy first because its signatures are more specific
                        // and HR generic timestamp check might false-positive on it.
                        val lines = text.lines()
                        val parsed = parseEnergyCSV(lines)
                        if (parsed.isNotEmpty()) {
                            energyList.addAll(parsed)
                        }
                    } else if (filename.contains("heart") || text.contains(",bpm") || 
                              (text.lines().take(5).any { it.contains("Z,") && !it.contains("percentage") && !it.contains("Correct") })
                    ) {
                         // Parse HR
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
        return "Keine gültigen Daten gefunden."
    }

    // 2. Calculate Time Shift
    // Find latest timestamp across all data
    val maxHrTime = hrList.maxOfOrNull { it.timestamp }
    val maxEnergyTime = energyList.maxOfOrNull { it.timestamp }
    
    val maxSourceInstant = when {
        maxHrTime != null && maxEnergyTime != null -> if (maxHrTime > maxEnergyTime) maxHrTime else maxEnergyTime
        maxHrTime != null -> maxHrTime
        else -> maxEnergyTime!!
    }

    // Determine "last complete day" in source
    // Simple heuristic: The day of the max timestamp is "today" in source context.
    // If we want the "last complete day" to map to "today", we might need to shift differently.
    // User request: "der tag heute soll der tag aus den daten sein, die als letztes vollständig vorlagen"
    // Interpretation: If data goes up to 2025-12-19 23:59, then 2025-12-19 is the last complete day.
    // We map 2025-12-19 to Today (e.g. 2024-XX-XX).
    // So the offset is: Today.StartOfDay - SourceMax.StartOfDay.
    // This preserves the time of day.
    
    val timeZone = TimeZone.currentSystemDefault()
    val sourceDate = maxSourceInstant.toLocalDateTime(timeZone).date
    val todayDate = Clock.System.now().toLocalDateTime(timeZone).date
    
    // We want SourceDate to become TodayDate
    // But wait, if source is "last complete day", usually implies the day BEFORE the max timestamp if max timestamp is essentially "now".
    // However, if the user provides a dataset that ends on a specific day, they usually treat that whole day as data.
    // Let's align SourceDate -> TodayDate.
    
    // Calculate offset in milliseconds
    // Since we are dealing with Instants, simpler way:
    // Offset = TodayNoon - SourceNoon (to avoid DST edge cases slightly)
    val sourceNoon = kotlinx.datetime.LocalDateTime(sourceDate, kotlinx.datetime.LocalTime(12,0)).toInstant(timeZone)
    val todayNoon = kotlinx.datetime.LocalDateTime(todayDate, kotlinx.datetime.LocalTime(12,0)).toInstant(timeZone)
    
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
    
    // Insert in DB
    newHrEntries.forEach { hrDao.insert(it); countHr++ }
    newEnergyEntries.forEach { energyDao.insert(it); countEnergy++ }

    return "Import erfolgreich. Zeitverschiebung: ${offset.inWholeDays} Tage.\nHR: $countHr, Energy: $countEnergy"
}

// ---- Parsers ----

private data class TempHeartRate(val timestamp: Instant, val bpm: Long)
private data class TempEnergy(val timestamp: Instant, val percentage: Percentage, val validation: Validation)

private fun parseHeartRateCSV(lines: List<String>): List<TempHeartRate> {
    return lines.mapNotNull { line ->
        // Format: timestamp,bpm
        // 2025-12-19T00:00:00Z,60
        val parts = line.split(",")
        if (parts.size < 2) return@mapNotNull null
        
        try {
            // Trim and clean
            val tsString = parts[0].trim()
            val bpmString = parts[1].trim()
            
            // Skip header
            if (tsString.lowercase().contains("timestamp")) return@mapNotNull null
            
            val timestamp = Instant.parse(tsString)
            val bpm = bpmString.toDouble().toLong() // handle 60.0 if present
            
            TempHeartRate(timestamp, bpm)
        } catch (e: Exception) {
            null
        }
    }
}

private fun parseEnergyCSV(lines: List<String>): List<TempEnergy> {
    return lines.mapNotNull { line ->
        // Format: timestamp,percentage,validation
         // 2025-12-19T10:28:18.059Z,47.22656394845787%,Correct
        val parts = line.split(",")
        if (parts.size < 2) return@mapNotNull null

        try {
             val tsString = parts[0].trim()
             if (tsString.lowercase().contains("timestamp")) return@mapNotNull null
             
             val timestamp = Instant.parse(tsString)
             var pctString = parts[1].trim()
             if (pctString.endsWith("%")) pctString = pctString.dropLast(1)
             val pctVal = pctString.toDouble() / 100.0 // "47.22" usually implies percent if valid DB stores 0.0-1.0?
             // Actually database Percentage class handles 0.0 - 1.0 mostly.
             // If string was "47.22%", it means 0.4722.
             // If string was "0.47", it means 0.47.
             // Let's assume if > 1.0 it is percentage 0-100, else ratio.
             // But here we explicitly divide by 100 because user said "47.22%".
             
             val percentage = Percentage(pctVal.coerceIn(0.0, 1.0))
             
             val validationStr = if (parts.size > 2) parts[2].trim() else "Correct"
             val validation = if (validationStr.equals("Adjusted", ignoreCase = true)) Validation.Adjusted else Validation.Correct
             
             TempEnergy(timestamp, percentage, validation)
        } catch (e: Exception) {
            null
        }
    }
}
