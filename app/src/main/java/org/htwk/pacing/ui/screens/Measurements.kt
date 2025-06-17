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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
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
import org.htwk.pacing.ui.components.AxisConfig
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.PathConfig
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.components.withFill
import org.htwk.pacing.ui.components.withStroke
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.seconds

@Composable
fun MeasurementsScreen(
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel(),
) {
    val series = viewModel.series.collectAsState().value

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

            Log.d("Graph", series.toString())

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
        }
    }
}

class MeasurementsViewModel(
    private val heartRateDao: HeartRateDao
) : ViewModel() {
    private val seriesMut = MutableStateFlow(Series(mutableListOf(), mutableListOf()))
    val series = seriesMut.asStateFlow()

    init {
        viewModelScope.launch {
            heartRateDao.getLastLive(10.seconds).collect { entries ->
                val updated = Series(mutableListOf(), mutableListOf())
                entries.forEach { (time, value) ->
                    updated.x.add(time.toEpochMilliseconds().toDouble())
                    updated.y.add(value.toDouble())
                }
                seriesMut.value = updated
            }
        }
    }
}