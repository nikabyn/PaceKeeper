package org.htwk.pacing.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import org.htwk.pacing.ui.math.interpolate
import kotlin.math.abs

/**
 * A series of values to be displayed by a graph component.
 *
 * User should ensure that values are sorted!
 */
data class Series<C : Collection<Double>>(val x: C, val y: C)

/**
 * Options for how the line graph should be drawn.
 *
 * User should change values based on dark/light theme!
 */
open class PathConfig(
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
fun PathConfig.withStroke(color: Color? = null, style: Stroke? = null) =
    PathConfig(color, style, this.fill, hasStroke = true, this.hasFill)

/**
 * Fill area under the graph with a flat color
 */
fun PathConfig.withFill(color: Color? = null) =
    PathConfig(this.color, this.style, color, this.hasStroke, hasFill = true)

/**
 * Options for displaying an axis (range, labels).
 *
 * @param range minimum and maximum values to be shown,
 *              dynamic by default: minimum and maximum of entire series
 * @param steps number of labels to be shown
 * @param formatFunction how a labels text should be formatted
 */
data class AxisConfig(
    val range: ClosedRange<Double>? = null,
    val steps: UInt? = null,
    val formatFunction: (value: Double) -> String = { value -> "%.1f".format(value) }
)

/**
 * A Card with a title that displays a line graph with two labelled axes.
 */
@Composable
fun <C : Collection<Double>> GraphCard(
    title: String,
    series: Series<C>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    pathConfig: PathConfig = PathConfig.withStroke(),
) {
    OutlinedCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            AnnotatedGraph(
                series = series,
                xConfig = xConfig,
                yConfig = yConfig,
                pathConfig = pathConfig,
            )
        }
    }
}

/**
 * A line graph with two labelled axes.
 *
 * User must set Modifier.height(...)!
 */
@Composable
fun <C : Collection<Double>> AnnotatedGraph(
    series: Series<C>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    pathConfig: PathConfig = PathConfig.withStroke(),
) {
    val xRange = xConfig.range ?: defaultRange(series.x)
    val xSteps = xConfig.steps ?: 3u;
    val xLabels = (0u..<xSteps).map { step ->
        val value =
            interpolate(
                xRange.start,
                xRange.endInclusive,
                step.toFloat() / (xSteps - 1u).toFloat()
            )
        xConfig.formatFunction(value)
    }

    val yRange = yConfig.range ?: defaultRange(series.y)
    val ySteps = yConfig.steps ?: 3u;
    val yLabels = (ySteps - 1u downTo 0u).map { step ->
        val value =
            interpolate(
                yRange.start,
                yRange.endInclusive,
                step.toFloat() / (ySteps - 1u).toFloat()
            )
        yConfig.formatFunction(value)
    }

    val xAxisHeightPx = remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val xAxisHeightDp = with(density) { xAxisHeightPx.intValue.toDp() }

    @Composable
    fun Label(text: String) = Text(text, style = MaterialTheme.typography.labelLarge)

    Row(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
            modifier = Modifier
                .fillMaxHeight()
                .padding(bottom = xAxisHeightDp)
        ) {
            yLabels.forEach { Label(it) }
        }

        Column(modifier = Modifier.weight(1f)) {
            Graph(
                series = series,
                yRange = yRange,
                xRange = xRange,
                pathConfig = pathConfig,
                modifier = Modifier
                    .padding(10.dp)
                    .weight(1f)
                    .drawLines(ySteps),
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { layoutCoordinates ->
                        xAxisHeightPx.intValue = layoutCoordinates.size.height
                    }
            ) {
                xLabels.forEach { Label(it) }
            }
        }
    }
}

private fun Modifier.drawLines(ySteps: UInt): Modifier = this.drawBehind {
    val path = Path();
    for (i in 0u..<ySteps) {
        val height = i.toFloat() / (ySteps.toFloat() - 1) * size.height
        path.moveTo(0f, height);
        path.lineTo(1f * size.width, height);
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
fun <C : Collection<Double>> Graph(
    series: Series<C>,
    modifier: Modifier = Modifier,
    xRange: ClosedRange<Double> = defaultRange(series.x),
    yRange: ClosedRange<Double> = defaultRange(series.y),
    pathConfig: PathConfig = PathConfig.withStroke(),
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
    ) {
        fun toXCoord(x: Double) = (x * size.width).toFloat()
        fun toYCoord(y: Double) = ((1.0f - y) * size.height).toFloat()
        fun toGraphCoords(x: Double, y: Double) = Pair(toXCoord(x), toYCoord(y))

        val path = Path()

        if (series.x.isEmpty() || series.y.isEmpty()) {
            return@Canvas
        }

        val start = toGraphCoords(relativeX.first(), relativeY.first())
        path.moveTo(start.first, start.second)

        var end = start
        for ((time, value) in relativeX.drop(1).zip(relativeY.drop(1))) {
            end = toGraphCoords(time, value)
            path.lineTo(end.first, end.second)
        }

        if (pathConfig.hasStroke) {
            drawPath(
                path,
                color = pathConfig.color ?: defaultColor,
                style = pathConfig.style ?: defaultStyle
            )
        }

        if (pathConfig.hasFill) {
            path.lineTo(end.first, toYCoord(0.0))
            path.lineTo(start.first, toYCoord(0.0))
            path.lineTo(start.first, start.second)

            drawPath(path, color = pathConfig.fill ?: defaultFill)
        }
    }
}

private fun defaultRange(values: Collection<Double>): ClosedRange<Double> {
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    return min..max
}
