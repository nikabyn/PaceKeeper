package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.ui.lineTo
import org.htwk.pacing.ui.math.Float2D
import org.htwk.pacing.ui.moveTo
import org.htwk.pacing.ui.relativeLineTo
import org.htwk.pacing.ui.toPx
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Shows a graph of the last 48 hours of the users heart rate
 * and a prediction for the next 6 hours.
 * The last value in the series is used as the current time.
 */
@Composable
fun <C : Collection<Double>, D: Collection<Double>> HeartRatePredictionCard(
    series: Series<C>,
    seriesPredicted: Series<D>,
    @FloatRange(from = 0.0, to = 1.0) minPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) avgPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) maxPrediction: Float,
    modifier: Modifier = Modifier,
) {
    CardWithTitle(title = "Heart Rate Prediction", modifier) {
        if (series.x.isEmpty()) {
            Text(
                "Currently no data available!",
                modifier = Modifier.testTag("EnergyPredictionErrorText")
            )
            return@CardWithTitle
        }

        val current = Instant.fromEpochMilliseconds(series.x.last().toLong())
        val start = (current - 48.hours).toEpochMilliseconds().toDouble()
        val end = (current + 6.hours).toEpochMilliseconds().toDouble()

        val yConfig = AxisConfig(range = 40.0..160.0, steps = 7u)
        val xConfig = AxisConfig(
            range = start..end,
            formatFunction = {
                val localTime =
                    Instant.fromEpochMilliseconds(it.toLong())
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                "%02d:%02d".format(localTime.hour, localTime.minute)
            }
        )

        Annotation(
            series = series,
            xConfig = xConfig,
            yConfig = yConfig,
        ) { _, yRange ->
            Row()
            {
                Graph(
                    series = series,
                    xRange = (current - 48.hours).toEpochMilliseconds().toDouble()..(current - 0.hours).toEpochMilliseconds().toDouble(),
                    yRange = yRange,
                    modifier = Modifier.weight(1f),
                )

                val pc = PathConfig(Color(0xFFF96B6B), null, null, hasStroke = true, false)

                Graph(
                    series = seriesPredicted,
                    xRange = (current - 0.hours).toEpochMilliseconds().toDouble()..(current + 6.hours).toEpochMilliseconds().toDouble(),
                    yRange = yRange,
                    modifier = Modifier.weight(1f),
                    pathConfig = pc
                )
            }
        }
    }
}