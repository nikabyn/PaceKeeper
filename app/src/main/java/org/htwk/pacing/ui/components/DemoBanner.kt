package org.htwk.pacing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.ModeDao
import org.htwk.pacing.backend.database.ModeEntry
import org.htwk.pacing.backend.database.PacingDatabase
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days


class ModeViewModel(
    private val db: PacingDatabase,
    private val modeDao: ModeDao
) : ViewModel() {
    val mode = modeDao.getModeLive().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch {
            modeDao.setMode(
                ModeEntry(
                    id = 0,
                    demo = enabled
                )
            )
        }
    }

    /**
     * Liest eine ZIP-Datei vom angegebenen Pfad ein und speichert die CSV-Daten in die Datenbank.
     * Der Zeitstempel wird so angepasst, dass die Daten 30 Tage vor dem aktuellen Zeitpunkt liegen.
     */
    fun storeDemoRecords(
        filePath: String
    ) = viewModelScope.launch {
        val file = File(filePath)

        if (!file.exists()) {
            // Falls die Datei nicht existiert, brechen wir ab
            return@launch
        }

        file.inputStream().use { fis ->
            ZipInputStream(fis).use { zis ->
                var entry = zis.nextEntry

                while (entry != null) {
                    // WICHTIG: Wir nutzen lineSequence(), damit der Reader
                    // den ZipInputStream nicht vorzeitig schließt.
                    val reader = zis.bufferedReader()
                    val rows = reader.lineSequence()
                        .filter { it.isNotBlank() }
                        .drop(1) // Header überspringen
                        .map { it.split(",") }
                        .toList()

                    if (rows.isNotEmpty()) {
                        val earliestTimestamp: Instant = rows.minOf { Instant.parse(it[0]) }
                        val now: Instant = Clock.System.now()
                        val offset: Duration = (now - earliestTimestamp) - 30.days

                        // Wir nutzen substringAfterLast("/"), falls die Dateien im ZIP in Ordnern liegen
                        when (entry.name.lowercase().substringAfterLast("/")) {

                            "steps.csv" ->
                                db.stepsDao().insertMany(
                                    rows.map {
                                        val startOriginal = Instant.parse(it[0])
                                        val endOriginal = Instant.parse(it[1])
                                        StepsEntry(
                                            start = startOriginal + offset,
                                            end = endOriginal + offset,
                                            count = it[2].toLong()
                                        )
                                    }
                                )

                            "heartrate.csv" ->
                                db.heartRateDao().insertMany(
                                    rows.map {
                                        HeartRateEntry(
                                            time = Instant.parse(it[0]) + offset,
                                            bpm = it[1].toLong()
                                        )
                                    }
                                )

                            "distance.csv" ->
                                db.distanceDao().insertMany(
                                    rows.map {
                                        val ts = Instant.parse(it[0]) + offset
                                        DistanceEntry(
                                            start = ts,
                                            end = ts,
                                            length = Length.meters(it[1].toDouble())
                                        )
                                    }
                                )

                            "elevation.csv" ->
                                db.elevationGainedDao().insertMany(
                                    rows.map {
                                        val ts = Instant.parse(it[0]) + offset
                                        ElevationGainedEntry(
                                            start = ts,
                                            end = ts,
                                            length = Length.meters(it[1].toDouble())
                                        )
                                    }
                                )

                            "skintemperature.csv" ->
                                db.skinTemperatureDao().insertMany(
                                    rows.map {
                                        SkinTemperatureEntry(
                                            time = Instant.parse(it[0]) + offset,
                                            temperature = Temperature.celsius(it[1].toDouble())
                                        )
                                    }
                                )

                            "sleep_session.csv" ->
                                db.sleepSessionsDao().insertMany(
                                    rows.map {
                                        SleepSessionEntry(
                                            start = Instant.parse(it[0]) + offset,
                                            end = Instant.fromEpochMilliseconds(it[1].toLong()) + offset,
                                            stage = SleepStage.valueOf(it[2].uppercase())
                                        )
                                    }
                                )

                            "predicted_energy_level.csv" ->
                                db.predictedEnergyLevelDao().insertMany(
                                    rows.map {
                                        PredictedEnergyLevelEntry(
                                            time = Instant.parse(it[0]) + offset,
                                            percentageNow = Percentage(it[1].toDouble()),
                                            timeFuture = Instant.parse(it[2]) + offset,
                                            percentageFuture = Percentage(it[3].toDouble())
                                        )
                                    }
                                )

                            "validated_energy_kevek.csv" ->
                                db.validatedEnergyLevelDao().insertMany(
                                    rows.map {
                                        ValidatedEnergyLevelEntry(
                                            time = Instant.parse(it[0]) + offset,
                                            percentage = Percentage(
                                                it[1].removeSuffix("%")
                                                    .toDouble()
                                                    .coerceIn(0.0, 100.0)
                                            ),
                                            validation = when (it[2].lowercase()) {
                                                "correct" -> Validation.Correct
                                                "adjusted" -> Validation.Adjusted
                                                else -> Validation.Correct
                                            }
                                        )
                                    }
                                )
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
    }
}

@Composable
fun DemoBanner(
    modeViewModel: ModeViewModel = koinViewModel(),
    minHeight: Dp = 32.dp,
) {
    //modeViewModel.setDemoMode(true)
    val mode by modeViewModel.mode.collectAsState()
    if (mode?.demo != true) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .background(Color(0xFFFF9800))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.demo_banner),
            color = Color.White
        )
    }
}
