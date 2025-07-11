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

/**
 * Shows a graph of the last 12 hours of the users energy level
 * and a prediction for the next 12 hours.
 * The last value in the series is used as the current time.
 */
@Composable
fun <C : Collection<Double>> EnergyPredictionCard(
    series: Series<C>,
    @FloatRange(from = 0.0, to = 1.0) minPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) avgPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) maxPrediction: Float,
    modifier: Modifier = Modifier,
) {
    CardWithTitle(title = "Energy Prediction", modifier) {
        if (series.x.isEmpty()) {
            Text(
                "Currently no data available!",
                modifier = Modifier.testTag("EnergyPredictionErrorText")
            )
            return@CardWithTitle
        }

        val current = Instant.fromEpochMilliseconds(series.x.last().toLong())
        val start = (current - 12.hours).toEpochMilliseconds().toDouble()
        val end = (current + 12.hours).toEpochMilliseconds().toDouble()

        val yConfig = AxisConfig(range = 0.0..1.0, steps = 0u)
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
            Row(
                modifier = Modifier.drawPrediction(
                    series.y.lastOrNull()?.toFloat() ?: 0.5f,
                    minPrediction,
                    avgPrediction,
                    maxPrediction
                )
            ) {
                Graph(
                    series = series,
                    xRange = start..current.toEpochMilliseconds().toDouble(),
                    yRange = yRange,
                    modifier = Modifier.weight(1f),
                )

                Box(modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun Modifier.drawPrediction(
    current: Float,
    minPrediction: Float,
    avgPrediction: Float,
    maxPrediction: Float
): Modifier = this.drawBehind {
    val scope = this
    val color = when {
        avgPrediction < 0.4f -> Color(0xFFF96B6B)
        avgPrediction < 0.6f -> Color(0xFFECC00A)
        else -> Color(0xFF8FE02A)
    }
    val current = Float2D(0.5f, 1f - current)

    val centerPath = Path().apply {
        moveTo(scope, Float2D(0.5f, 0f))
        lineTo(scope, Float2D(0.5f, 1f))
    }
    drawPath(
        centerPath,
        color = Color.Gray,
        style = Stroke(
            width = 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 20f))
        ),
    )

    val predictionArea = Path().apply {
        moveTo(scope, Float2D(1f, 1f - maxPrediction))
        lineTo(scope, current)
        lineTo(scope, Float2D(1f, 1f - minPrediction))
        close()
    }
    drawPath(
        predictionArea,
        brush = Brush.linearGradient(
            0f to color.copy(alpha = 0.5f),
            1f to color.copy(alpha = 0.0f),
            start = current.toPx(size),
            end = Float2D(1f, 0.5f).toPx(size),
        )
    )

    val predictionDirection = Path().apply {
        moveTo(scope, current)
        val scale = 0.3f
        val direction = Float2D(0.5f, 0.5f - avgPrediction)
            .normalize()
            .scale(scale)
        relativeLineTo(scope, direction)
    }
    drawPath(
        predictionDirection,
        color = color,
        style = Stroke(5f)
    )
}

