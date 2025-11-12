package org.htwk.pacing.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import org.htwk.pacing.R
import org.htwk.pacing.backend.heuristics.HeartRateZones


@Composable
fun <C : Collection<Double>> HeartRateGraphCard(
    title: String,
    series: Series<C>,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    modifier: Modifier = Modifier,
    zonesResult: HeartRateZones.HeartRateZonesResult
) = CardWithTitle(title, modifier) {
    val zoneColors = listOf(
        colorResource(R.color.green_700), // green: healthZone
        colorResource(R.color.cyan_700), // cyan: recoveryZone
        colorResource(R.color.yellow_700), // yellow: exertionZone
        colorResource(R.color.red_700)  // red: area above threshold
    )
    val strokeStyle = Graph.defaultStrokeStyle()

    Annotation(
        series = series,
        xConfig = xConfig,
        yConfig = yConfig,
    ) { xRange, yRange ->
        GraphCanvas {
            val totalRange = yRange.endInclusive - yRange.start

            // Definiere alle Grenzpunkte als Double
            val boundaries = listOf(
                yRange.start,
                zonesResult.visualHealthZone.endInclusive.toDouble(),
                zonesResult.recoveryZone.endInclusive.toDouble(),
                zonesResult.exertionZone.endInclusive.toDouble(),
                //zonesResult.anaerobicThreshold,
                yRange.endInclusive
            )

            // Zeichne kontinuierliche Bereiche zwischen den Grenzen
            boundaries.zip(boundaries.drop(1)).forEachIndexed { index, (start, end) ->
                if (index < zoneColors.size) {
                    val canvasTop =
                        size.height * (1f - ((end - yRange.start) / totalRange).toFloat())
                    val canvasBottom =
                        size.height * (1f - ((start - yRange.start) / totalRange).toFloat())
                    val height = canvasBottom - canvasTop

                    if (height > 0f) {
                        drawRect(
                            color = zoneColors[index],
                            topLeft = Offset(0f, canvasTop),
                            size = Size(size.width, height)
                        )
                    }
                }
            }

            val paths = graphToPaths(series, size, xRange, yRange)
            drawPath(paths.line, color = Color.White, style = strokeStyle)
        }
    }
}




