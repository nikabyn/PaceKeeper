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
import androidx.compose.material3.Button
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
import org.htwk.pacing.backend.database.DistanceDao
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedDao
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityDao
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.MenstruationPeriodDao
import org.htwk.pacing.backend.database.MenstruationPeriodEntry
import org.htwk.pacing.backend.database.OxygenSaturationDao
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.SkinTemperatureDao
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionDao
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedDao
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsDao
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.database.ValidatedEnergyLevelDao
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.backend.database.Velocity
import org.htwk.pacing.ui.theme.CardStyle
import org.htwk.pacing.ui.theme.PrimaryButtonStyle
import org.htwk.pacing.ui.theme.Spacing
import java.util.zip.ZipInputStream
import kotlin.time.Duration.Companion.seconds

@Composable
fun ZipDataImport_import_temp(
    heartRateDao: HeartRateDao,
    validatedEnergyLevelDao: ValidatedEnergyLevelDao,
    distanceDao: DistanceDao,
    elevationGainedDao: ElevationGainedDao,
    predictedEnergyLevelDao: PredictedEnergyLevelDao,
    heartRateVariabilityDao: HeartRateVariabilityDao,
    menstruationPeriodDao: MenstruationPeriodDao,
    oxygenSaturationDao: OxygenSaturationDao,
    skinTemperatureDao: SkinTemperatureDao,
    sleepSessionsDao: SleepSessionDao,
    speedDao: SpeedDao,
    stepsDao: StepsDao
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
                "Importiert alle Daten aus einer ZIP-Datei.",
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
                colors = PrimaryButtonStyle.colors
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
                                importZipData(
                                    context,
                                    uri!!,
                                    heartRateDao,
                                    validatedEnergyLevelDao,
                                    distanceDao,
                                    elevationGainedDao,
                                    predictedEnergyLevelDao,
                                    heartRateVariabilityDao,
                                    menstruationPeriodDao,
                                    oxygenSaturationDao,
                                    skinTemperatureDao,
                                    sleepSessionsDao,
                                    speedDao,
                                    stepsDao
                                )
                            } catch (e: Exception) {
                                Log.e("ZipImport", "Error", e)
                                "Error: ${e.message}"
                            }
                            status = resultMsg
                            isImporting = false
                        }
                    }
                },
                colors = PrimaryButtonStyle.colors,
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
    validatedEnergyDao: ValidatedEnergyLevelDao,
    distanceDao: DistanceDao,
    elevationDao: ElevationGainedDao,
    predictedEnergyDao: PredictedEnergyLevelDao,
    hrvDao: HeartRateVariabilityDao,
    menstruationDao: MenstruationPeriodDao,
    oxygenDao: OxygenSaturationDao,
    skinTempDao: SkinTemperatureDao,
    sleepDao: SleepSessionDao,
    speedDao: SpeedDao,
    stepsDao: StepsDao
): String {
    val contentResolver = context.contentResolver

    // Lists for all data types
    val hrList = mutableListOf<TempHeartRate>()
    val validatedEnergyList = mutableListOf<TempValidatedEnergy>()
    val distanceList = mutableListOf<TempDistance>()
    val elevationList = mutableListOf<TempElevation>()
    val predictedEnergyList = mutableListOf<TempPredictedEnergy>()
    val hrvList = mutableListOf<TempHrv>()
    val menstruationList = mutableListOf<TempMenstruation>()
    val oxygenList = mutableListOf<TempOxygen>()
    val skinTempList = mutableListOf<TempSkinTemp>()
    val sleepList = mutableListOf<TempSleep>()
    val speedList = mutableListOf<TempSpeed>()
    val stepsList = mutableListOf<TempSteps>()
    val allLists = listOf(
        hrList, validatedEnergyList, distanceList, elevationList, predictedEnergyList, hrvList,
        menstruationList, oxygenList, skinTempList, sleepList, speedList, stepsList
    )
    val allTimestamps = mutableListOf<Instant>()

    // 1. Read ZIP
    contentResolver.openInputStream(uri)?.use { inputStream ->
        ZipInputStream(inputStream).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val filename = entry.name.lowercase()
                    val lines = String(zipStream.readBytes()).lines().drop(1)

                    when {
                        filename == "heart_rate.csv" -> hrList.addAll(parseHeartRateCSV(lines))
                        filename == "validated_energy_level.csv" -> validatedEnergyList.addAll(
                            parseValidatedEnergyCSV(lines)
                        )

                        filename == "distance.csv" -> distanceList.addAll(parseDistanceCSV(lines))
                        filename == "elevation.csv" -> elevationList.addAll(parseElevationCSV(lines))
                        filename == "energy_level.csv" -> predictedEnergyList.addAll(
                            parsePredictedEnergyCSV(lines)
                        )

                        filename == "heart_rate_variability.csv" -> hrvList.addAll(parseHrvCSV(lines))
                        filename == "menstruation.csv" -> menstruationList.addAll(
                            parseMenstruationCSV(lines)
                        )

                        filename == "oxygen_saturation.csv" -> oxygenList.addAll(
                            parseOxygenSaturationCSV(lines)
                        )

                        filename == "skin_temperature.csv" -> skinTempList.addAll(
                            parseSkinTemperatureCSV(lines)
                        )

                        filename == "sleep_sessions.csv" -> sleepList.addAll(
                            parseSleepSessionsCSV(
                                lines
                            )
                        )

                        filename == "speed.csv" -> speedList.addAll(parseSpeedCSV(lines))
                        filename == "steps.csv" -> stepsList.addAll(parseStepsCSV(lines))
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
        }
    }

    if (allLists.all { it.isEmpty() }) {
        return "Keine gültigen Daten in der ZIP-Datei gefunden."
    }

    // 2. Calculate Time Shift
    allTimestamps.addAll(hrList.map { it.timestamp })
    allTimestamps.addAll(validatedEnergyList.map { it.timestamp })
    allTimestamps.addAll(distanceList.map { it.start })
    allTimestamps.addAll(elevationList.map { it.start })
    allTimestamps.addAll(predictedEnergyList.map { it.timestamp })
    allTimestamps.addAll(hrvList.map { it.timestamp })
    allTimestamps.addAll(menstruationList.map { it.start })
    allTimestamps.addAll(oxygenList.map { it.timestamp })
    allTimestamps.addAll(skinTempList.map { it.timestamp })
    allTimestamps.addAll(sleepList.map { it.start })
    allTimestamps.addAll(speedList.map { it.timestamp })
    allTimestamps.addAll(stepsList.map { it.start })

    val maxSourceInstant = allTimestamps.maxOrNull() ?: return "Keine Zeitstempel gefunden."

    val timeZone = TimeZone.currentSystemDefault()
    val sourceDate = maxSourceInstant.toLocalDateTime(timeZone).date
    val todayDate = Clock.System.now().toLocalDateTime(timeZone).date
    val sourceNoon = kotlinx.datetime.LocalDateTime(sourceDate, kotlinx.datetime.LocalTime(12, 0))
        .toInstant(timeZone)
    val todayNoon = kotlinx.datetime.LocalDateTime(todayDate, kotlinx.datetime.LocalTime(12, 0))
        .toInstant(timeZone)
    val offset = todayNoon - sourceNoon

    // 3. Shift and Insert
    hrList.forEach { hrDao.insert(HeartRateEntry(time = it.timestamp + offset, bpm = it.bpm)) }
    validatedEnergyList.forEach {
        validatedEnergyDao.insert(
            ValidatedEnergyLevelEntry(
                time = it.timestamp + offset,
                validation = it.validation,
                percentage = it.percentage
            )
        )
    }
    distanceList.forEach {
        distanceDao.insert(
            DistanceEntry(
                start = it.start + offset,
                end = it.start + offset + 1.seconds,
                length = Length(it.distanceMeters)
            )
        )
    }
    elevationList.forEach {
        elevationDao.insert(
            ElevationGainedEntry(
                start = it.start + offset,
                end = it.start + offset + 1.seconds,
                length = Length(it.elevationMeters)
            )
        )
    }

    hrvList.forEach {
        hrvDao.insert(
            HeartRateVariabilityEntry(
                time = it.timestamp + offset,
                variability = it.variability
            )
        )
    }
    menstruationList.forEach {
        menstruationDao.insert(
            MenstruationPeriodEntry(
                start = it.start + offset,
                end = it.end + offset
            )
        )
    }
    oxygenList.forEach {
        oxygenDao.insert(
            OxygenSaturationEntry(
                time = it.timestamp + offset,
                percentage = Percentage(it.saturation),
            )
        )
    }
    skinTempList.forEach {
        skinTempDao.insert(
            SkinTemperatureEntry(
                time = it.timestamp + offset,
                temperature = Temperature(it.tempCelsius)
            )
        )
    }
    sleepList.forEach {
        sleepDao.insert(
            SleepSessionEntry(
                start = it.start + offset,
                end = it.end + offset,
                stage = it.stage
            )
        )
    }
    speedList.forEach {
        speedDao.insert(
            SpeedEntry(
                time = it.timestamp + offset,
                velocity = Velocity(it.speed)
            )
        )
    }
    stepsList.forEach {
        stepsDao.insert(
            StepsEntry(
                start = it.start + offset,
                end = it.end + offset,
                count = it.count
            )
        )
    }

    val summary = listOf(
        "HR" to hrList.size,
        "ValidatedEnergy" to validatedEnergyList.size,
        "Distance" to distanceList.size,
        "Elevation" to elevationList.size,
        "PredictedEnergy" to predictedEnergyList.size,
        "HRV" to hrvList.size,
        "Menstruation" to menstruationList.size,
        "Oxygen" to oxygenList.size,
        "SkinTemp" to skinTempList.size,
        "Sleep" to sleepList.size,
        "Speed" to speedList.size,
        "Steps" to stepsList.size
    ).filter { it.second > 0 }.joinToString("\n") { "${it.first}: ${it.second}" }

    return "Import erfolgreich. Zeitverschiebung: ${offset.inWholeDays} Tage.\n$summary"
}

// ---- Temporary Data Classes ----
private data class TempHeartRate(val timestamp: Instant, val bpm: Long)
private data class TempValidatedEnergy(
    val timestamp: Instant,
    val percentage: Percentage,
    val validation: Validation
)

private data class TempDistance(val start: Instant, val distanceMeters: Double)
private data class TempElevation(val start: Instant, val elevationMeters: Double)
private data class TempPredictedEnergy(val timestamp: Instant, val energyLevel: Double)
private data class TempHrv(val timestamp: Instant, val variability: Double)
private data class TempMenstruation(val start: Instant, val end: Instant)
private data class TempOxygen(val timestamp: Instant, val saturation: Double)
private data class TempSkinTemp(val timestamp: Instant, val tempCelsius: Double)
private data class TempSleep(val start: Instant, val end: Instant, val stage: SleepStage)
private data class TempSpeed(val timestamp: Instant, val speed: Double)
private data class TempSteps(val start: Instant, val end: Instant, val count: Long)

// ---- Parsers ----
private fun parseHeartRateCSV(lines: List<String>): List<TempHeartRate> = lines.mapNotNull { line ->
    try {
        line.split(",").let { p -> TempHeartRate(Instant.parse(p[0]), p[1].toLong()) }
    } catch (e: Exception) {
        null
    }
}

private fun parseValidatedEnergyCSV(lines: List<String>): List<TempValidatedEnergy> =
    lines.mapNotNull { line ->
        try {
            line.split(",").let { p ->
                val pctVal = p[2].trimEnd('%').toDouble() / 100.0
                val validation =
                    if (p[1].equals("Adjusted", true)) Validation.Adjusted else Validation.Correct
                TempValidatedEnergy(Instant.parse(p[0]), Percentage(pctVal), validation)
            }
        } catch (e: Exception) {
            null
        }
    }

private fun parseDistanceCSV(lines: List<String>): List<TempDistance> = lines.mapNotNull { line ->
    try {
        line.split(",").let { p -> TempDistance(Instant.parse(p[0]), p[1].toDouble()) }
    } catch (e: Exception) {
        null
    }
}

private fun parseElevationCSV(lines: List<String>): List<TempElevation> = lines.mapNotNull { line ->
    try {
        line.split(",").let { p -> TempElevation(Instant.parse(p[0]), p[1].toDouble()) }
    } catch (e: Exception) {
        null
    }
}

private fun parsePredictedEnergyCSV(lines: List<String>): List<TempPredictedEnergy> =
    lines.mapNotNull { line ->
        try {
            line.split(",").let { p -> TempPredictedEnergy(Instant.parse(p[0]), p[1].toDouble()) }
        } catch (e: Exception) {
            null
        }
    }

private fun parseHrvCSV(lines: List<String>): List<TempHrv> = lines.mapNotNull { line ->
    try {
        line.split(",").let { p -> TempHrv(Instant.parse(p[0]), p[1].toDouble()) }
    } catch (e: Exception) {
        null
    }
}

private fun parseMenstruationCSV(lines: List<String>): List<TempMenstruation> =
    lines.mapNotNull { line ->
        try {
            line.split(",").let { p ->
                TempMenstruation(
                    Instant.parse(p[0]),
                    Instant.fromEpochMilliseconds(p[1].toLong())
                )
            }
        } catch (e: Exception) {
            null
        }
    }

private fun parseOxygenSaturationCSV(lines: List<String>): List<TempOxygen> =
    lines.mapNotNull { line ->
        try {
            line.split(",").let { p -> TempOxygen(Instant.parse(p[0]), p[1].toDouble()) }
        } catch (e: Exception) {
            null
        }
    }

private fun parseSkinTemperatureCSV(lines: List<String>): List<TempSkinTemp> =
    lines.mapNotNull { line ->
        try {
            line.split(",").let { p -> TempSkinTemp(Instant.parse(p[0]), p[1].toDouble()) }
        } catch (e: Exception) {
            null
        }
    }

private fun parseSleepSessionsCSV(lines: List<String>): List<TempSleep> = lines.mapNotNull { line ->
    try {
        line.split(",").let { p ->
            val stage = SleepStage.valueOf(p[2])
            TempSleep(Instant.parse(p[0]), Instant.fromEpochMilliseconds(p[1].toLong()), stage)
        }
    } catch (e: Exception) {
        null
    }
}

private fun parseSpeedCSV(lines: List<String>): List<TempSpeed> = lines.mapNotNull { line ->
    try {
        line.split(",").let { p -> TempSpeed(Instant.parse(p[0]), p[1].toDouble()) }
    } catch (e: Exception) {
        null
    }
}

private fun parseStepsCSV(lines: List<String>): List<TempSteps> = lines.mapNotNull { line ->
    try {
        line.split(",").let { p ->
            TempSteps(
                Instant.parse(p[0]),
                Instant.fromEpochMilliseconds(p[1].toLong()),
                p[2].toLong()
            )
        }
    } catch (e: Exception) {
        null
    }
}
