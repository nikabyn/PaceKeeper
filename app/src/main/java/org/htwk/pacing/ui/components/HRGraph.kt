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
            .testTag("HRGraphCard")
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
 * A line graph including colored heart rate zones.
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
            Color(0x3300FF00), // green: healthZone
            Color(0x3300FFFF), // cyan: recoveryZone
            Color(0x33FFFF00), // yellow: exertionZone
            Color(0x33FF0000)  // red: area above threshold
        )
        listOf(
            zonesResult.visualHealthZone,
            zonesResult.recoveryZone,
            zonesResult.exertionZone
        ).forEachIndexed { index, zone ->

            val highlightMin = zone.start.toDouble()
            val highlightMax = zone.endInclusive.toDouble()

            // Draw zones only within y-range
            if (highlightMax >= yRange.start && highlightMin <= yRange.endInclusive) {

                val visibleMin = maxOf(highlightMin, yRange.start)
                val visibleMax = minOf(highlightMax, yRange.endInclusive)

                // calculate rel. position
                val relativeMin = (visibleMin - yRange.start) / (yRange.endInclusive - yRange.start)
                val relativeMax = (visibleMax - yRange.start) / (yRange.endInclusive - yRange.start)

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

            val relativeMin = (highlightMin - yRange.start) / (yRange.endInclusive - yRange.start)
            val relativeMax = (highlightMax - yRange.start) / (yRange.endInclusive - yRange.start)

            val yCanvasTop = size.height * (1f - relativeMax.toFloat())
            val yCanvasBottom = size.height * (1f - relativeMin.toFloat())

            drawRect(
                color = zoneColors[3], // red
                topLeft = Offset(0f, yCanvasTop),
                size = Size(size.width, yCanvasBottom - yCanvasTop)
            )
        }

        fun toXCord(x: Double) = x.toFloat()
        fun toYCord(y: Double) = (1.0f - y).toFloat()
        fun toGraphCords(x: Double, y: Double) = Float2D(toXCord(x), toYCord(y))

        val path = Path()

        if (series.x.isEmpty() || series.y.isEmpty()) {
            return@Canvas
        }

        val start = toGraphCords(relativeX.first(), relativeY.first())
        path.moveTo(scope, start)

        var end = start
        for ((time, value) in relativeX.drop(1).zip(relativeY.drop(1))) {
            end = toGraphCords(time, value)
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
            path.lineTo(scope, Float2D(end.x, toYCord(0.0)))
            path.lineTo(scope, Float2D(start.x, toYCord(0.0)))
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

