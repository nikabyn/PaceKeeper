package org.htwk.pacing.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.htwk.pacing.backend.heuristics.HeartRateZones
import org.htwk.pacing.ui.lineTo
import org.htwk.pacing.ui.math.Float2D
import org.htwk.pacing.ui.math.interpolate
import org.htwk.pacing.ui.moveTo
import kotlin.math.abs

/**
 * A series of values to be displayed by a graph component.
 *
 * User should ensure that values are sorted!
 */
//data class HRSeries<C : Collection<Double>>(val x: C, val y: C)

/**
 * Options for how the line graph should be drawn.
 *
 * User should change values based on dark/light theme!
 */
open class HRPathConfig(
    internal val color: Color? = null,
    internal val style: Stroke? = null,
    internal val fill: Color? = null,
    internal val hasStroke: Boolean = false,
    internal val hasFill: Boolean = false,
) {
    companion object : PathConfig()
}

/**
 * Draw lines between all points (linear, no special interpolation)
 */
fun HRPathConfig.withStroke(color: Color? = null, style: Stroke? = null) =
    PathConfig(color, style, this.fill, hasStroke = true, this.hasFill)

/**
 * Fill area under the graph with a flat color
 */
fun HRPathConfig.withFill(color: Color? = null) =
    PathConfig(this.color, this.style, color, this.hasStroke, hasFill = true)

/**
 * Options for displaying an axis (range, labels).
 *
 * @param range minimum and maximum values to be shown,
 *              dynamic by default: minimum and maximum of entire series
 * @param steps number of labels to be shown
 * @param formatFunction how a labels text should be formatted
 */
data class HRAxisConfig(
    val range: ClosedRange<Double>? = null,
    val steps: UInt? = null,
    val formatFunction: (value: Double) -> String = { value -> "%.1f".format(value) }
)

/**
 * A Card with a title that displays a line graph with two labelled axes.
 */
@Composable
fun <C : Collection<Double>> HRGraphCard(
    title: String,
    series: Series<C>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    pathConfig: PathConfig = PathConfig.withStroke(),
    zonesResult: HeartRateZones.HeartRateZonesResult
) {
    CardWithTitle(
        title = title,
        modifier = modifier
            .height(300.dp)
            .testTag("GraphCard")
    ) {
        HRAnnotatedGraph(
            series = series,
            xConfig = xConfig,
            yConfig = yConfig,
            pathConfig = pathConfig,
            zonesResult = zonesResult
        )
    }
}

/**
 * A line graph with two labelled axes.
 *
 * User must set Modifier.height(...)!
 */
@Composable
fun <C : Collection<Double>> HRAnnotatedGraph(
    series: Series<C>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    pathConfig: PathConfig = PathConfig.withStroke(),
    zonesResult: HeartRateZones.HeartRateZonesResult
) {
    HRAnnotation(series, modifier, xConfig, yConfig) { xRange, yRange ->
        HRGraph(
            series = series,
            yRange = yRange,
            xRange = xRange,
            pathConfig = pathConfig,
            zonesResult = zonesResult
        )
    }
}

/**
 * Axis annotations, has a slot for a Graph or some other component.
 */
@Composable
fun <C : Collection<Double>> HRAnnotation(
    series: Series<C>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    slot: @Composable ((yRange: ClosedRange<Double>, xRange: ClosedRange<Double>) -> Unit) =
        { _, _ -> Box(modifier = Modifier.fillMaxSize()) { } },
) {
    val xRange = xConfig.range ?: defaultRange(series.x)
    val xSteps = xConfig.steps ?: 3u;
    val xLabels: List<String> = when (xSteps) {
        0u -> emptyList()
        1u -> listOf(xConfig.formatFunction(xRange.start))
        else -> (0u..<xSteps).map { step ->
            val t = step.toFloat() / (xSteps - 1u).toFloat()
            val value = interpolate(xRange.start, xRange.endInclusive, t)
            xConfig.formatFunction(value)
        }
    }

    val yRange = yConfig.range ?: defaultRange(series.y)
    val ySteps = yConfig.steps ?: 3u;
    val yLabels: List<String> = when (ySteps) {
        0u -> emptyList()
        1u -> listOf(yConfig.formatFunction(yRange.start))
        else -> (0u..<ySteps).reversed().map { step ->
            val t = step.toFloat() / (ySteps - 1u).toFloat()
            val value = interpolate(yRange.start, yRange.endInclusive, t)
            yConfig.formatFunction(value)
        }
    }

    val xAxisHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val xAxisHeightDp = with(density) { xAxisHeightPx.intValue.toDp() }

    @Composable
    fun Label(text: String) = Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.testTag("AxisLabel")
    )

    Row(modifier = modifier.testTag("AnnotatedGraph")) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .fillMaxHeight()
                .padding(bottom = xAxisHeightDp)
                .testTag("yAxis")
        ) {
            yLabels.forEach { Label(it) }
        }

        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .weight(1f)
                    .drawLines(ySteps)
            ) {
                slot(xRange, yRange)
            }

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        xAxisHeightPx.intValue = layoutCoordinates.size.height
                    }
                    .testTag("xAxis")
            ) {
                xLabels.forEach { Label(it) }
            }
        }
    }
}

private fun Modifier.drawLines(ySteps: UInt): Modifier = this.drawBehind {
    val scope = this

    val path = Path().apply {
        for (i in 0u..<ySteps) {
            val height = i.toFloat() / (ySteps.toFloat() - 1)
            moveTo(scope, Float2D(0f, height))
            lineTo(scope, Float2D(1f, height))
        }
    }
    drawPath(
        path,
        color = Color.Gray,
        style = Stroke(
            width = 1.0f,
            pathEffect = PathEffect.dashPathEffect(
                // TODO: Figure out how to scale this properly based on screen size
                floatArrayOf(20f, 8f)
            )
        )
    )
}

/**
 * A line graph.
 *
 * User must set Modifier.height(...)!
 */
@Composable
fun <C : Collection<Double>> HRGraph(
    series: Series<C>,
    modifier: Modifier = Modifier,
    xRange: ClosedRange<Double> = defaultRange(series.x),
    yRange: ClosedRange<Double> = defaultRange(series.y),
    pathConfig: PathConfig = PathConfig.withStroke(),
    zonesResult: HeartRateZones.HeartRateZonesResult
) {
    val defaultColor = if (isSystemInDarkTheme()) Color.White else Color.Black
    val defaultStyle = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val defaultFill =
        if (isSystemInDarkTheme()) Color(1.0f, 1.0f, 1.0f, 0.1f) else Color(0.0f, 0.0f, 0.0f, 0.1f)


    val relativeX = series.x.map { xValue ->
        (xValue - xRange.start) / abs(xRange.endInclusive - xRange.start)
    }
    val relativeY = series.y.map { yValue ->
        (yValue - yRange.start) / abs(yRange.endInclusive - yRange.start)
    }


    Canvas(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .testTag("Graph")
    ) {
        val scope = this
        val zoneColors = listOf(
            Color(0x3300FF00), // Grün für Health Zone
            Color(0x3300FFFF), // Cyan für Recovery Zone
            Color(0x33FFFF00), // Gelb für Exertion Zone
            Color(0x33FF0000)  // Rot für den Bereich über der anaeroben Schwelle
        )
        listOf(
            zonesResult.healthZone,
            zonesResult.recoveryZone,
            zonesResult.exertionZone
        ).forEachIndexed { index, zone ->

            val highlightMin = zone.start.toDouble()
            val highlightMax = zone.endInclusive.toDouble()

            // Stelle sicher, dass die Zone innerhalb des sichtbaren Y-Bereichs liegt
            if (highlightMax > yRange.start && highlightMin < yRange.endInclusive) {

                // Begrenze die Werte auf den sichtbaren Bereich
                val visibleMin = maxOf(highlightMin, yRange.start)
                val visibleMax = minOf(highlightMax, yRange.endInclusive)

                // Berechne die relativen Positionen
                val relativeMin = (visibleMin - yRange.start) / (yRange.endInclusive - yRange.start)
                val relativeMax = (visibleMax - yRange.start) / (yRange.endInclusive - yRange.start)

                // Berechne die Canvas-Positionen
                val yCanvasTop = size.height * (1f - relativeMax.toFloat())
                val yCanvasBottom = size.height * (1f - relativeMin.toFloat())

                // Zeichne den farbigen Bereich mit der entsprechenden Farbe
                drawRect(
                    color = zoneColors.getOrNull(index) ?: Color(0x33AAAAAA),
                    topLeft = Offset(0f, yCanvasTop),
                    size = Size(size.width, yCanvasBottom - yCanvasTop)
                )
            }
        }
        // Zusätzlich: Zeichne den Bereich über der anaeroben Schwelle (falls gewünscht)
        val anaerobicThreshold = zonesResult.anaerobicThreshold
        if (anaerobicThreshold > yRange.start && anaerobicThreshold < yRange.endInclusive) {
            val highlightMin = anaerobicThreshold
            val highlightMax = yRange.endInclusive

            val relativeMin = (highlightMin - yRange.start) / (yRange.endInclusive - yRange.start)
            val relativeMax = (highlightMax - yRange.start) / (yRange.endInclusive - yRange.start)

            val yCanvasTop = size.height * (1f - relativeMax.toFloat())
            val yCanvasBottom = size.height * (1f - relativeMin.toFloat())

            drawRect(
                color = zoneColors[3], // Rote Farbe für Bereich über anaerober Schwelle
                topLeft = Offset(0f, yCanvasTop),
                size = Size(size.width, yCanvasBottom - yCanvasTop)
            )
        }

        fun toXCoord(x: Double) = x.toFloat()
        fun toYCoord(y: Double) = (1.0f - y).toFloat()
        fun toGraphCoords(x: Double, y: Double) = Float2D(toXCoord(x), toYCoord(y))

        val path = Path()

        if (series.x.isEmpty() || series.y.isEmpty()) {
            return@Canvas
        }

        val start = toGraphCoords(relativeX.first(), relativeY.first())
        path.moveTo(scope, start)

        var end = start
        for ((time, value) in relativeX.drop(1).zip(relativeY.drop(1))) {
            end = toGraphCoords(time, value)
            path.lineTo(scope, end)
        }

        if (pathConfig.hasStroke) {
            drawPath(
                path,
                color = pathConfig.color ?: defaultColor,
                style = pathConfig.style ?: defaultStyle
            )
        }

        if (pathConfig.hasFill) {
            path.lineTo(scope, Float2D(end.x, toYCoord(0.0)))
            path.lineTo(scope, Float2D(start.x, toYCoord(0.0)))
            path.lineTo(scope, start)

            drawPath(path, color = pathConfig.fill ?: defaultFill)
        }
    }
}

private fun defaultRange(values: Collection<Double>): ClosedRange<Double> {
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    return min..max
}

