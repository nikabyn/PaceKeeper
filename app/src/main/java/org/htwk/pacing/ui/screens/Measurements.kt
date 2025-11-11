package org.htwk.pacing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedHeartRateDao
import org.htwk.pacing.backend.heuristics.HeartRateZones
import org.htwk.pacing.ui.components.AxisConfig
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.HRGraphCard
import org.htwk.pacing.ui.components.HeartRatePredictionCard
import org.htwk.pacing.ui.components.PathConfig
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.components.withFill
import org.htwk.pacing.ui.components.withStroke
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

@Composable
fun MeasurementsScreen(
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel(),
) {
    val series by viewModel.heartRate.collectAsState()
    val feelingLevels by viewModel.feelingLevels.collectAsState()
    val predictedHeartRate by viewModel.predictedHeartRate.collectAsState()
    val predictedEnergyLevel by viewModel.predictedEnergyLevel.collectAsState()

    val input = HeartRateZones.HeartRateInput(23, HeartRateZones.Gender.FEMALE, 50)
    var timeNow by remember { mutableStateOf(Clock.System.now()) }
    var time7daysAgo by remember { mutableStateOf(timeNow - 7.days) }
    var time12hoursAgo by remember { mutableStateOf(timeNow - 12.hours) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            timeNow = Clock.System.now()
            time7daysAgo = timeNow - 7.days
            time12hoursAgo = timeNow - 12.hours
        }
    }

    val pathConfig = PathConfig
        .withStroke(
            color = if (isSystemInDarkTheme()) lerp(Color.Red, Color.White, 0.5f) else Color.Red,
            style = Stroke(width = 1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        ).withFill(
            color = Color.hsv(0.0f, 0.5f, 1.0f, 0.3f)
        )

    Box(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(30.dp),
            modifier = Modifier.padding(all = 40.dp)
        ) {
            fun formatTime(value: Double): String {
                val localTime =
                    Instant.fromEpochMilliseconds(value.toLong())
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                return "%02d:%02d:%02d".format(localTime.hour, localTime.minute, localTime.second)
            }

            HRGraphCard(
                title = stringResource(R.string.heart_rate_last_7_days),
                modifier = Modifier.height(200.dp),
                series = series,
                xConfig = AxisConfig(
                    formatFunction = {
                        val localTime = Instant.fromEpochMilliseconds(it.toLong())
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        "%02d.%02d.%02d".format(
                            localTime.dayOfMonth,
                            localTime.monthNumber,
                            localTime.year
                        )
                    },
                    range = time7daysAgo.toEpochMilliseconds().toDouble()
                            ..timeNow.toEpochMilliseconds().toDouble(),
                    steps = 2u,
                ),
                yConfig = AxisConfig(
                    range = 0.0..160.0,
                    steps = 3u,
                ),
                pathConfig = pathConfig,
                zonesResult = HeartRateZones.calculateZones(input)
            )

            HRGraphCard(
                title = stringResource(R.string.heart_rate_last_12_hours),
                series = series,
                xConfig = AxisConfig(
                    formatFunction = {
                        val localTime = Instant.fromEpochMilliseconds(it.toLong())
                            .toLocalDateTime(TimeZone.currentSystemDefault())
                        "%02d:%02d:%02d".format(
                            localTime.hour,
                            localTime.minute,
                            localTime.second
                        )
                    },
                    range = time12hoursAgo.toEpochMilliseconds().toDouble()
                            ..timeNow.toEpochMilliseconds().toDouble(),
                    steps = 2u,
                ),
                yConfig = AxisConfig(
                    range = 40.0..160.0
                ),
                pathConfig = pathConfig,
                zonesResult = HeartRateZones.calculateZones(input)
            )
            HeartRatePredictionCard(
                title = stringResource(R.string.heart_rate_prediction),
                series = series,
                seriesPredicted = predictedHeartRate,
                yConfig = AxisConfig(range = 40.0..160.0, steps = 7u),
                modifier = Modifier.height(300.dp)
            )

            GraphCard(
                title = stringResource(R.string.energy_level_debug_prediction),
                series = predictedEnergyLevel,
                xConfig = AxisConfig(
                    formatFunction = ::formatTime,
                    steps = 2u,
                ),
                yConfig = AxisConfig(range = 0.0..1.0, steps = 5u),
                modifier = Modifier.height(300.dp)
            )

            GraphCard(
                title = stringResource(R.string.feeling_manual_symptoms),
                series = feelingLevels,
                xConfig = AxisConfig(
                    formatFunction = ::formatTime,
                    steps = 2u,
                ),
                yConfig = AxisConfig(
                    formatFunction = { Feeling.fromInt(it.toInt()).name },
                    steps = 4u,
                ),
            )
        }
    }
}

class MeasurementsViewModel(
    heartRateDao: HeartRateDao,
    manualSymptomDao: ManualSymptomDao,
    predictedHeartRateDao: PredictedHeartRateDao,
    predictedEnergyLevelDao: PredictedEnergyLevelDao,
) : ViewModel() {
    val feelingLevels = manualSymptomDao
        .getLastLive(1.days)
        .map {
            val updated = Series(mutableListOf(), mutableListOf())
            it.forEach { (feelingEntry, _) ->
                updated.x.add(feelingEntry.time.toEpochMilliseconds().toDouble())
                updated.y.add(feelingEntry.feeling.level.toDouble())
            }
            updated
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Series(emptyList(), emptyList())
        )

    val heartRate = heartRateDao
        .getLastLive(30.days)
        .map { entries ->
            val updated = Series(mutableListOf(), mutableListOf())
            entries.forEach { (time, value) ->
                updated.x.add(time.toEpochMilliseconds().toDouble())
                updated.y.add(value.toDouble())
            }
            updated
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Series(emptyList(), emptyList())
        )

    val predictedHeartRate = predictedHeartRateDao
        .getAllLive()
        .map { entries ->
            val updated = Series(mutableListOf(), mutableListOf())
            entries.forEach { (time, value) ->
                updated.x.add(time.toEpochMilliseconds().toDouble())
                updated.y.add(value.toDouble())
            }
            updated
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Series(emptyList(), emptyList())
        )

    val predictedEnergyLevel = predictedEnergyLevelDao
        .getAllLive()
        .map { entries ->
            val updated = Series(mutableListOf(), mutableListOf())
            entries.forEach { (time, value) ->
                updated.x.add(time.toEpochMilliseconds().toDouble())
                updated.y.add(value.toDouble())
            }
            updated
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = Series(emptyList(), emptyList())
        )
}