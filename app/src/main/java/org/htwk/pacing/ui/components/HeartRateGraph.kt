package org.htwk.pacing.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import org.htwk.pacing.R
import org.htwk.pacing.backend.heuristics.HeartRateZones

/**
 * A composable that displays a heart rate graph with colored zones indicating different intensity levels.
 *
 * @param title The title to display in the card header
 * @param series The data series containing heart rate values to plot
 * @param xConfig Configuration for the X-axis
 * @param yConfig Configuration for the Y-axis
 * @param modifier Modifier for styling and layout
 * @param zonesResult Contains the heart rate zone boundaries for coloring the graph background
 * @param C The type of collection containing the heart rate data (must extend Collection<Double>)
 */
@Composable
fun <C : Collection<Double>> HeartRateGraphCard(
    title: String,
    series: Series<C>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    zonesResult: HeartRateZones.HeartRateZonesResult
) = CardWithTitle(title, modifier) {

    // Define colors for different heart rate zones (from low to high intensity)
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

            // Define the boundaries between heart rate zones
            // These values determine where each color zone starts and ends
            val boundaries = listOf(
                yRange.start,
                zonesResult.visualHealthZone.endInclusive.toDouble(),
                zonesResult.recoveryZone.endInclusive.toDouble(),
                zonesResult.exertionZone.endInclusive.toDouble(),
                yRange.endInclusive
            )

            // Draw colored rectangles for each heart rate zone
            // Pair each boundary with the next one to create zones, then draw them
            boundaries.zip(boundaries.drop(1)).forEachIndexed { index, (start, end) ->
                // Calculate canvas position for upper border
                // Note: Canvas Y=0 is at top, so we invert the calculation
                val canvasTop =
                    size.height * (1f - ((end - yRange.start) / totalRange).toFloat())
                // Calculate canvas position for lower border
                val canvasBottom =
                    size.height * (1f - ((start - yRange.start) / totalRange).toFloat())

                // Calculate height of zone in pixels
                val height = canvasBottom - canvasTop

                // Only draw if zone has positive height (avoids rendering errors)
                if (height <= 0f) return@forEachIndexed

                drawRect(
                    color = zoneColors[index],
                    topLeft = Offset(0f, canvasTop),
                    size = Size(size.width, height)
                )
            }

            val paths = graphToPaths(series, size, xRange, yRange)
            drawPath(paths.line, color = Color.White, style = strokeStyle)
        }
    }
}




