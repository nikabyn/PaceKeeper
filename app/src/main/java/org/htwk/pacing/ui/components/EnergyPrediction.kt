package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.R
import org.htwk.pacing.ui.lineTo
import org.htwk.pacing.ui.math.Float2D
import org.htwk.pacing.ui.moveTo
import org.htwk.pacing.ui.theme.extendedColors
import org.htwk.pacing.ui.toPx
import kotlin.time.Duration.Companion.hours

/**
 * Shows a graph of the last 6 hours of the users energy level
 * and a prediction for the next 6 hours.
 * The last value in the series is used as the current time.
 */
@Composable
fun <C : Collection<Double>> EnergyPredictionCard(
    series: Series<C>,
    @FloatRange(from = 0.0, to = 1.0) minPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) avgPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) maxPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) currentEnergy: Float,
    modifier: Modifier = Modifier,
) {
    CardWithTitle(title = stringResource(R.string.energy_prediction), modifier) {
        if (series.x.isEmpty()) {
            Text(
                stringResource(R.string.currently_no_data_available),
                modifier = Modifier.testTag("EnergyPredictionErrorText")
            )
            return@CardWithTitle
        }

        val current = Clock.System.now()
        val start = (current - 6.hours).toEpochMilliseconds().toDouble()
        val end = (current + 6.hours).toEpochMilliseconds().toDouble()

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
                    currentEnergy,
                    minPrediction,
                    avgPrediction,
                    maxPrediction,
                    predictionPositiveColor = MaterialTheme.extendedColors.green,
                    predictionConstantColor = MaterialTheme.extendedColors.yellow,
                    predictionNegativeColor = MaterialTheme.extendedColors.red,
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
    currentEnergy: Float,
    minPrediction: Float,
    avgPrediction: Float,
    maxPrediction: Float,
    predictionPositiveColor: Color,
    predictionConstantColor: Color,
    predictionNegativeColor: Color,
): Modifier = this.drawBehind {
    val scope = this
    val color = when {
        avgPrediction < 0.4f -> predictionNegativeColor
        avgPrediction < 0.6f -> predictionConstantColor
        else -> predictionPositiveColor
    }
    val current = Float2D(0.5f, 1f - currentEnergy)

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
        val scale = 0.5f

        val targetY = (avgPrediction - currentEnergy) * 0.5f + currentEnergy
        lineTo(
            scope,
            Float2D(0.75f, 1f - targetY)
        )
    }
    drawPath(
        predictionDirection,
        color = color,
        style = Stroke(5f)
    )
}

