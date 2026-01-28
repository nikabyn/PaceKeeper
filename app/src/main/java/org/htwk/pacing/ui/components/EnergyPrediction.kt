package org.htwk.pacing.ui.components

import androidx.annotation.FloatRange
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import kotlinx.datetime.Clock
import org.htwk.pacing.R
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.ui.screens.measurements.TimeRange
import org.htwk.pacing.ui.theme.extendedColors
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.hours

/**
 * Shows a graph of the last 6 hours of the users energy level
 * and a prediction for the next 6 hours.
 * The last value in the series is used as the current time.
 */
@Composable
fun EnergyPredictionCard(
    data: List<PredictedEnergyLevelEntry>,
    @FloatRange(from = 0.0, to = 1.0) minPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) avgPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) maxPrediction: Float,
    @FloatRange(from = 0.0, to = 1.0) currentEnergy: Float,
    modifier: Modifier = Modifier,
) {
    CardWithTitle(title = stringResource(R.string.energy_prediction), modifier) {
        val current = Clock.System.now()
        val start = current - 6.hours
        val end = current + 6.hours

        val xRange = TimeRange(start, end).toEpochDoubleRange()
        val yRange = 0.0..1.0
        val (xData, yData) = data
            .map { Pair(it.time.toEpochMilliseconds().toDouble(), it.percentageNow.toDouble()) }
            .unzip()

        val graphLineColor = MaterialTheme.colorScheme.onSurface
        val centerLineColor = MaterialTheme.colorScheme.outline
        val predictionPositiveColor = MaterialTheme.extendedColors.green
        val predictionConstantColor = MaterialTheme.extendedColors.yellow
        val predictionNegativeColor = MaterialTheme.extendedColors.red

        GraphCanvas(
            Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val graphStrokeStyle = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
            val dashedStrokeStyle = Stroke(
                width = 2.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(
                    intervals = floatArrayOf(
                        10.dp.toPx(),
                        10.dp.toPx()
                    ),
                    phase = 5.dp.toPx()
                )
            )

            val paths = graphToPaths(
                xData = xData,
                yData = yData,
                size = size.copy(width = size.width),
                xRange = xRange,
                yRange = yRange,
            )
            clipRect(right = size.width / 2f) {
                drawPath(
                    paths.line,
                    graphLineColor,
                    style = graphStrokeStyle
                )
            }

            val predictionColor = when {
                avgPrediction < 0.4f -> predictionNegativeColor
                avgPrediction < 0.6f -> predictionConstantColor
                else -> predictionPositiveColor
            }

            drawPredictionArea(
                currentEnergy,
                minPrediction,
                maxPrediction,
                predictionColor,
            )

            drawPredictionLine(
                currentEnergy,
                avgPrediction,
                predictionColor,
                dashedStrokeStyle,
            )

            drawCenterLine(
                centerLineColor,
                dashedStrokeStyle,
            )
        }

        Axis(horizontal = true) {
            AxisLabelHourMinutes(start)
            AxisLabelHourMinutes(current)
            AxisLabelHourMinutes(end)
        }
    }
}

private fun DrawScope.drawPredictionArea(
    currentEnergy: Float,
    minPrediction: Float,
    maxPrediction: Float,
    color: Color,
) {
    val width = size.width
    val height = size.height

    val path = Path()
    path.moveTo(0.5f * width, (1f - currentEnergy) * height)
    path.lineTo(width, (1f - minPrediction) * height)
    path.lineTo(width, (1f - maxPrediction) * height)
    path.close()

    drawPath(
        path,
        brush = Brush.linearGradient(
            0f to color.copy(alpha = 0.4f),
            1f to color.copy(alpha = 0.0f),
            start = Offset(width / 2f, (1f - currentEnergy) * height),
            end = Offset(width, (1f - currentEnergy) * height),
        )
    )
}

private fun DrawScope.drawPredictionLine(
    currentEnergy: Float,
    avgPrediction: Float,
    color: Color,
    style: DrawStyle,
) {
    val width = size.width
    val height = size.height

    val start = Offset(0.5f * width, (1f - currentEnergy) * height)
    val targetY = (avgPrediction - currentEnergy) * 0.5f + currentEnergy
    val end = Offset(0.75f * width, (1f - targetY) * height)

    drawArrowLine(
        start = start,
        end = end,
        color = color,
        stroke = style,
    )
}

private fun DrawScope.drawCenterLine(
    color: Color,
    style: DrawStyle,
) {
    val width = size.width
    val height = size.height

    val top = Offset(0.5f * width, 0f)
    val bottom = Offset(0.5f * width, height)

    drawArrowLine(
        start = top,
        end = bottom,
        color = color,
        stroke = style,
        startArrow = ArrowHead.Backwards,
        endArrow = ArrowHead.Backwards,
    )
}

private fun DrawScope.drawArrowLine(
    start: Offset,
    end: Offset,
    color: Color,
    stroke: DrawStyle,
    arrowSize: Float = 12.dp.toPx(),
    startArrow: ArrowHead = ArrowHead.None,
    endArrow: ArrowHead = ArrowHead.Forwards,
) {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val length = sqrt(dx * dx + dy * dy)
    if (length == 0f) return

    // normalised direction vector
    val ux = dx / length
    val uy = dy / length

    val halfWidth = arrowSize * 0.5f

    // Calculate line start/end, leaving space for arrowheads
    val realStart = when (startArrow) {
        ArrowHead.None -> start
        else -> Offset(start.x + ux * arrowSize, start.y + uy * arrowSize)
    }

    val realEnd = when (endArrow) {
        ArrowHead.None -> end
        else -> Offset(end.x - ux * arrowSize, end.y - uy * arrowSize)
    }

    // Draw main line (shortened to leave space for arrowheads)
    drawPath(
        Path().apply {
            moveTo(realStart.x, realStart.y)
            lineTo(realEnd.x, realEnd.y)
        },
        color,
        style = stroke
    )

    // Helper to draw a rounded triangle arrowhead
    fun drawRoundedArrow(tip: Offset, directionX: Float, directionY: Float) {
        val baseCenter = Offset(
            tip.x - directionX * arrowSize,
            tip.y - directionY * arrowSize
        )
        val left = Offset(
            baseCenter.x - directionY * halfWidth,
            baseCenter.y + directionX * halfWidth
        )
        val right = Offset(
            baseCenter.x + directionY * halfWidth,
            baseCenter.y - directionX * halfWidth
        )

        val poly = RoundedPolygon(
            vertices = floatArrayOf(
                tip.x, tip.y,
                left.x, left.y,
                right.x, right.y
            ),
            rounding = CornerRounding(radius = 1.dp.toPx(), smoothing = 1f)
        )

        drawPath(poly.toPath().asComposePath(), color)
    }

    // Draw start arrowhead
    when (startArrow) {
        ArrowHead.Forwards -> drawRoundedArrow(start, -ux, -uy)
        ArrowHead.Backwards -> drawRoundedArrow(realStart, ux, uy) // <-- tip at shortened base
        ArrowHead.None -> {}
    }

    // Draw end arrowhead
    when (endArrow) {
        ArrowHead.Forwards -> drawRoundedArrow(end, ux, uy)
        ArrowHead.Backwards -> drawRoundedArrow(realEnd, -ux, -uy) // <-- tip at shortened base
        ArrowHead.None -> {}
    }
}


private enum class ArrowHead {
    None,
    Forwards,
    Backwards,
}