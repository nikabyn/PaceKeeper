package org.htwk.pacing.ui.screens

import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.backend.database.HeartRateDao
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
    val heartRateSeries = viewModel.heartRateSeries.collectAsState().value
    val predictedHeartRateSeries = viewModel.predictedHeartRateSeries.collectAsState().value
    val predictedEnergyLevelSeries = viewModel.predictedEnergySeries.collectAsState().value

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

            Log.d("Graph", heartRateSeries.toString())

            GraphCard(
                title = "Heart Rate [bpm]",
                series = heartRateSeries,
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
                series = heartRateSeries,
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
                series = heartRateSeries,
                xConfig = AxisConfig(
                    formatFunction = ::formatTime,
                    steps = 2u,
                ),
                yConfig = AxisConfig(steps = 4u),
            )

            val now = Clock.System.now()

            HeartRatePredictionCard(
                /*series = Series(
                    inputX.map { it.toEpochMilliseconds().toDouble() },
                    inputY.toList(),
                ),
                seriesPredicted = Series(
                    predictionsX.map { it.toEpochMilliseconds().toDouble() },
                    predictionsY),*/
                /*series = Series(
                    listOf(
                        now - 12.hours,
                        now - 11.hours,
                        now - 10.hours,
                        now - 6.hours,
                        now - 4.hours,
                        now - 2.hours,
                        now
                    ).map { it.toEpochMilliseconds().toDouble() },
                    listOf(0.8, 0.82, 0.7, 0.65, 0.67, 0.45, 0.4),
                ),*/
                series = heartRateSeries,
                seriesPredicted = Series(predictedHeartRateSeries.x, predictedHeartRateSeries.y),
                minPrediction = 0.1f,
                avgPrediction = 0.35f,
                maxPrediction = 0.4f,
            )
        }
    }
}

class MeasurementsViewModel(
    private val heartRateDao: HeartRateDao,
    private val predictedHeartRateDao: PredictedHeartRateDao,
    private val predictedEnergyLevelDao: PredictedEnergyLevelDao
) : ViewModel() {
    private val predictedHeartRateSeriesMut = MutableStateFlow(Series(mutableListOf(), mutableListOf()))
    val predictedHeartRateSeries = predictedHeartRateSeriesMut.asStateFlow()

    private val predictedEnergySeriesMut = MutableStateFlow(Series(mutableListOf(), mutableListOf()))
    val predictedEnergySeries = predictedEnergySeriesMut.asStateFlow()

    private val heartRateSeriesMut = MutableStateFlow(Series(mutableListOf(), mutableListOf()))
    val heartRateSeries = heartRateSeriesMut.asStateFlow()

    init {
        viewModelScope.launch {
            heartRateDao.getLastLive(10.seconds).collect { entries ->
                val updated = Series(mutableListOf(), mutableListOf())
                entries.forEach { (time, value) ->
                    updated.x.add(time.toEpochMilliseconds().toDouble())
                    updated.y.add(value.toDouble())
                }
                heartRateSeriesMut.value = updated
            }
        }
        viewModelScope.launch {
            //TODO / REVIEW: is this ok? (see TimedSeries Interface change: getAllLive)
            predictedHeartRateDao.getAllLive().collect { entries ->
                val updated = Series(mutableListOf(), mutableListOf())
                entries.forEach { (time, value) ->
                    updated.x.add(time.toEpochMilliseconds().toDouble())
                    updated.y.add(value.toDouble())
                }
                predictedHeartRateSeriesMut.value = updated
            }
        }

        viewModelScope.launch {
            //TODO / REVIEW: is this ok? (see TimedSeries Interface change: getAllLive)
            predictedEnergyLevelDao.getAllLive().collect { entries ->
                val updated = Series(mutableListOf(), mutableListOf())
                entries.forEach { (time, value) ->
                    updated.x.add(time.toEpochMilliseconds().toDouble())
                    updated.y.add(value.toDouble())
                }
                predictedEnergySeriesMut.value = updated
            }
        }
    }
}