package org.htwk.pacing.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.heuristics.HeartRateZones
import org.htwk.pacing.ui.theme.extendedColors

/**
 * A composable that displays a heart rate graph with colored zones indicating different intensity levels.
 *
 * @param title The title to display in the card header
 * @param data The data series containing heart rate values to plot
 * @param xConfig Configuration for the X-axis
 * @param yConfig Configuration for the Y-axis
 * @param modifier Modifier for styling and layout
 * @param zonesResult Contains the heart rate zone boundaries for coloring the graph background
 */
@Composable
fun HeartRateGraphCard(
    title: String,
    data: List<HeartRateEntry>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    zonesResult: HeartRateZones.HeartRateZonesResult
) = CardWithTitle(title, modifier) {

    // Define colors for different heart rate zones (from low to high intensity)
    val zoneColors = listOf(
        MaterialTheme.extendedColors.green, // green: healthZone
        MaterialTheme.extendedColors.cyan, // cyan: recoveryZone
        MaterialTheme.extendedColors.yellow, // yellow: exertionZone
        MaterialTheme.extendedColors.red  // red: area above threshold
    )
    val strokeStyle = Graph.defaultStrokeStyle()
    val strokeColor =
        if (isSystemInDarkTheme()) Color.hsv(0f, 0f, 0.9f) else Color.hsv(0f, 0f, 0.1f)

    val (xData, yData) = data.map {
        Pair(
            it.time.toEpochMilliseconds().toDouble(),
            it.bpm.toDouble()
        )
    }.unzip()

    Annotation(
        xData,
        yData,
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

            val paths = graphToPaths(xData, yData, size, xRange, yRange)

            onDrawBehind {
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
                        alpha = 0.25f,
                        topLeft = Offset(0f, canvasTop),
                        size = Size(size.width, height),
                    )
                }

                drawPath(
                    paths.line,
                    color = strokeColor,
                    style = strokeStyle,
                    blendMode = BlendMode.Overlay
                )
            }
        }
    }
}




