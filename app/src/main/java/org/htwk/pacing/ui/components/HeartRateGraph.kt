package org.htwk.pacing.ui.components

import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.util.fastMap
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.heuristics.HeartRateZones
import org.htwk.pacing.ui.components.graph.graphToPaths
import org.htwk.pacing.ui.screens.measurements.TimeRange


fun CacheDrawScope.drawHeartRateGraph(
    entries: List<HeartRateEntry>,
    range: TimeRange,
    zoneColors: Array<Color>,
    zonesResult: HeartRateZones.HeartRateZonesResult,
    strokeColor: Color,
    strokeWidth: Float = 2f,
): DrawResult {
    val xData = entries.fastMap { it.time.toEpochMilliseconds().toDouble() }
    val yData = entries.fastMap { it.bpm.toDouble() }
    val xRange = range.toEpochDoubleRange()
    val yRange = 0.0..150.0 //TODO

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

    return onDrawBehind {
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
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
