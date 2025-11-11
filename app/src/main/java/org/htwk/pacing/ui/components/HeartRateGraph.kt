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
            listOf(
                zonesResult.visualHealthZone,
                zonesResult.recoveryZone,
                zonesResult.exertionZone
            ).forEachIndexed { index, zone ->
/*
                val zoneBoundaries = listOf(
                    yRange.start,
                    zonesResult.visualHealthZone.endInclusive.toDouble(),
                    zonesResult.recoveryZone.endInclusive.toDouble(),
                    zonesResult.exertionZone.endInclusive.toDouble(),
                    yRange.endInclusive
                )
                zoneBoundaries.zip(other = zoneBoundaries.drop(n = 1))
                    .forEachIndexed { index, (zoneStart, zoneEnd) ->
                        if (index < zoneColors.size) {
                            val relativeMin =
                                (zoneStart - yRange.start) / (yRange.endInclusive - yRange.start)
                            val relativeMax =
                                (zoneStart - yRange.start) / (yRange.endInclusive - yRange.start)
*/

                val highlightMin = zone.start.toDouble()
                val highlightMax = zone.endInclusive.toDouble()

                // Draw zones only within y-range
                if (highlightMax >= yRange.start && highlightMin <= yRange.endInclusive) {

                    val visibleMin = maxOf(highlightMin, yRange.start)
                    val visibleMax = minOf(highlightMax, yRange.endInclusive)

                    // calculate rel. position
                    val relativeMin =
                        (visibleMin - yRange.start) / (yRange.endInclusive - yRange.start)
                    val relativeMax =
                        (visibleMax - yRange.start) / (yRange.endInclusive - yRange.start)


                    // calculate position on canvas
                    val yCanvasTop = size.height * (1f - relativeMax.toFloat())
                    val yCanvasBottom = size.height * (1f - relativeMin.toFloat())

                    // draw zones
                    drawRect(
                        color = zoneColors.getOrNull(index) ?: Color(0x33AAAAAA),
                        topLeft = Offset(0f, yCanvasTop),
                        size = Size(size.width, yCanvasBottom - yCanvasTop)
                    )
                }
            }


            // Fill area above threshold
            val anaerobicThreshold = zonesResult.anaerobicThreshold
            if (anaerobicThreshold > yRange.start && anaerobicThreshold < yRange.endInclusive) {
                val highlightMin = anaerobicThreshold
                val highlightMax = yRange.endInclusive

                val relativeMin =
                    (highlightMin - yRange.start) / (yRange.endInclusive - yRange.start)
                val relativeMax =
                    (highlightMax - yRange.start) / (yRange.endInclusive - yRange.start)

                val yCanvasTop = size.height * (1f - relativeMax.toFloat())
                val yCanvasBottom = size.height * (1f - relativeMin.toFloat())

                drawRect(
                    color = zoneColors[3], // red
                    topLeft = Offset(0f, yCanvasTop),
                    size = Size(size.width, yCanvasBottom - yCanvasTop)
                )
            }

            val paths = graphToPaths(series, size, xRange, yRange)

            drawPath(paths.line, color = Color.White, style = strokeStyle)
        }
    }
}

