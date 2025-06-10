package org.htwk.pacing.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.randomHeartRate
import org.htwk.pacing.ui.components.AxisConfig
import org.htwk.pacing.ui.components.GraphCard
import org.htwk.pacing.ui.components.PathConfig
import org.htwk.pacing.ui.components.Series
import org.htwk.pacing.ui.components.withFill
import org.htwk.pacing.ui.components.withStroke

@Composable
fun MeasurementsScreen(modifier: Modifier = Modifier) {
    var series = remember { Series(mutableStateListOf(), mutableStateListOf()) }

    LaunchedEffect(Unit) {
        randomHeartRate(avgDelayMs = 10).collect { (value, time) ->
            series.x.add(time.toEpochMilliseconds().toDouble())
            series.y.add(value.toDouble())
        }
    }

    Box(
        modifier = Modifier
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
                return "%02d:%02d".format(localTime.hour, localTime.minute)
            }

            GraphCard(
                title = "Heart Rate [bpm]",
                series = series,
                xConfig = AxisConfig(
                    range = {
                        val timeZone = TimeZone.currentSystemDefault()
                        val start = LocalDateTime.parse("2025-01-01T00:00").toInstant(timeZone)
                            .toEpochMilliseconds().toDouble()
                        val end = LocalDateTime.parse("2025-01-01T23:59").toInstant(timeZone)
                            .toEpochMilliseconds().toDouble()
                        start..end
                    }(),
                    formatFunction = ::formatTime,
                ),
                yConfig = AxisConfig(
                    range = 0.0..120.0,
                    steps = 3u,
                ),
                pathConfig = PathConfig.withStroke().withFill(),
            )
            GraphCard(
                title = "Heart Rate [bpm], Filled",
                series = series,
                xConfig = AxisConfig(formatFunction = ::formatTime),
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
                xConfig = AxisConfig(formatFunction = ::formatTime),
                yConfig = AxisConfig(steps = 4u),
            )
        }
    }
}
