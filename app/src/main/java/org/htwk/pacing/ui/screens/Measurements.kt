package org.htwk.pacing.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.HeartRateDao
import org.htwk.pacing.backend.database.ManualSymptomDao
import org.htwk.pacing.backend.database.PredictedEnergyLevelDao
import org.htwk.pacing.backend.database.PredictedHeartRateDao
import org.htwk.pacing.backend.mlmodel.MLModel
import org.htwk.pacing.ui.components.AxisConfig
import org.htwk.pacing.ui.components.Graph
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.HeartRatePredictionCard
import org.htwk.pacing.ui.components.PathConfig
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.components.withFill
import org.htwk.pacing.ui.components.withStroke
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.days
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
@Composable
fun MeasurementsScreen(
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel(),
) {
    val series by viewModel.heartRate.collectAsState()
    val feelingLevels by viewModel.feelingLevels.collectAsState()
    val predictedHeartRate by viewModel.predictedHeartRate.collectAsState()

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

            GraphCard(
                title = "Heart Rate [bpm]",
                series = series,
                xConfig = AxisConfig(
                    formatFunction = ::formatTime,
                    steps = 2u,
                ),
                yConfig = AxisConfig(
                    range = 0.0..120.0,
                    steps = 4u,
                ),
                pathConfig = PathConfig.withStroke().withFill(),
            )

            GraphCard(
                title = "Heart Rate [bpm], Filled",
                series = series,
                xConfig = AxisConfig(
                    formatFunction = ::formatTime,
                    steps = 2u,
                ),
                yConfig = AxisConfig(range = 0.0..120.0),
                pathConfig = PathConfig
                    .withStroke(
                        color = if (isSystemInDarkTheme()) {
                            lerp(Color.Red, Color.White, 0.5f)
                        } else {
                            Color.Red
                        },
                        style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round),
                    ).withFill(
                        color = Color.hsv(0.0f, 0.5f, 1.0f, 0.3f)
                    ),
            )

            GraphCard(
                title = "Heart Rate [bpm], Dynamic Range",
                series = series,
                xConfig = AxisConfig(
                    formatFunction = ::formatTime,
                    steps = 2u,
                ),
                yConfig = AxisConfig(steps = 4u),
            )

            val now = Clock.System.now()

            HeartRatePredictionCard(
                series = series,
                seriesPredicted = predictedHeartRate,
                minPrediction = 0.1f,
                avgPrediction = 0.35f,
                maxPrediction = 0.4f,
            )

            GraphCard(
                title = "Feeling, Manual Symptoms",
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
    private val heartRateDao: HeartRateDao,
    private val predictedHeartRateDao: PredictedHeartRateDao,
    private val predictedEnergyLevelDao: PredictedEnergyLevelDao,
    private val manualSymptomDao: ManualSymptomDao
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
        .getLastLive(10.seconds)
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