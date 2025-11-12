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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.htwk.pacing.ui.lineTo
import org.htwk.pacing.ui.math.Float2D
import org.htwk.pacing.ui.math.interpolate
import org.htwk.pacing.ui.moveTo

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
    CardWithTitle(
        title = title,
        modifier = modifier
            .height(300.dp)
            .testTag("GraphCard")
    ) {
        AnnotatedGraph(
            series = series,
            xConfig = xConfig,
            yConfig = yConfig,
            pathConfig = pathConfig,
        )
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
    Annotation(series, modifier, xConfig, yConfig) { xRange, yRange ->
        Graph(
            series = series,
            yRange = yRange,
            xRange = xRange,
            pathConfig = pathConfig,
        )
    }
}

/**
 * Axis annotations, has a slot for a Graph or some other component.
 */
@Composable
fun <C : Collection<Double>> Annotation(
    series: Series<C>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    slot: @Composable ((yRange: ClosedRange<Double>, xRange: ClosedRange<Double>) -> Unit) =
        { _, _ -> Box(modifier = Modifier.fillMaxSize()) { } },
) {
    val xRange = xConfig.range ?: Graph.defaultRange(series.x)
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

    val yRange = yConfig.range ?: Graph.defaultRange(series.y)
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
 * Draws a simple line graph for the given numeric [series].
 *
 * The graph supports both stroke and fill rendering through [pathConfig].
 * A height must be set with `Modifier.height(...)`.
 *
 * @param C The type of collection containing numeric data points.
 * @param series The data series to render.
 * @param modifier Modifier for layout and styling.
 * @param xRange Range of X-axis values to display. Defaults to the min–max of [series.x].
 * @param yRange Range of Y-axis values to display. Defaults to the min–max of [series.y].
 * @param pathConfig Configuration for stroke and fill styles.
 */
@Composable
fun <C : Collection<Double>> Graph(
    series: Series<C>,
    modifier: Modifier = Modifier,
    xRange: ClosedRange<Double> = Graph.defaultRange(series.x),
    yRange: ClosedRange<Double> = Graph.defaultRange(series.y),
    pathConfig: PathConfig = PathConfig.withStroke(),
) {
    val fillColor = pathConfig.fill ?: Graph.defaultFillColor()
    val strokeColor = pathConfig.color ?: Graph.defaultStrokeColor()
    val strokeStyle = pathConfig.style ?: Graph.defaultStrokeStyle()

    GraphCanvas(modifier) {
        val paths = graphToPaths(series, size, xRange, yRange)

        if (pathConfig.hasFill) {
            drawPath(paths.fill, fillColor)
        }

        if (pathConfig.hasStroke) {
            drawPath(paths.line, strokeColor, style = strokeStyle)
        }
    }
}

/**
 * Contains default configuration and helper functions for [Graph].
 */
object Graph {
    @Composable
    fun defaultStrokeColor() = if (isSystemInDarkTheme()) Color.White else Color.Black

    @Composable
    fun defaultFillColor() =
        if (isSystemInDarkTheme()) Color.Red.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.1f)

    fun defaultStrokeStyle() = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    fun defaultRange(values: Collection<Double>): ClosedRange<Double> {
        val min = values.minOrNull() ?: 0.0
        val max = values.maxOrNull() ?: 0.0
        return min..max
    }
}

/**
 * Composable canvas optimized for graph rendering.
 *
 * Applies GPU offscreen compositing for efficient redrawing.
 *
 * @param modifier Modifier for layout and styling.
 * @param onDraw Drawing logic executed inside the [DrawScope].
 */
@Composable
fun GraphCanvas(
    modifier: Modifier = Modifier,
    onDraw: DrawScope.() -> Unit = { drawRect(color = Color.Magenta) }
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer() {
                // Cache the drawing as a GPU texture
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .testTag("GraphCanvas"),
        onDraw = onDraw,
    )
}

/**
 * Holds the paths used for rendering a graph: the main line and the filled area beneath it.
 *
 * @property line The path representing the graph line.
 * @property fill The path representing the filled region under the graph.
 */
data class GraphPaths(val line: Path, val fill: Path)

/**
 * Converts a numeric [series] into drawable [Path] objects representing
 * the line and filled area of a graph.
 *
 * @param C The type of numeric collection.
 * @param series The data series to convert into paths.
 * @param size The canvas size for coordinate mapping.
 * @param xRange The visible X-axis range.
 * @param yRange The visible Y-axis range.
 * @return A [GraphPaths] object containing line and fill paths.
 */
fun <C : Collection<Double>> graphToPaths(
    series: Series<C>,
    size: Size,
    xRange: ClosedRange<Double> = Graph.defaultRange(series.x),
    yRange: ClosedRange<Double> = Graph.defaultRange(series.y),
): GraphPaths {
    val linePath = Path()

    if (series.x.isEmpty() || series.y.isEmpty()) {
        return GraphPaths(linePath, Path())
    }

    val posX = series.x.map { xValue ->
        (((xValue - xRange.start) / (xRange.endInclusive - xRange.start)).toFloat() * size.width)
    }
    val posY = series.y.map { yValue ->
        ((1f - ((yValue - yRange.start) / (yRange.endInclusive - yRange.start))).toFloat() * size.height)
    }

    linePath.moveTo(posX.first(), posY.first())
    for ((x, y) in posX.drop(1).zip(posY.drop(1))) {
        linePath.lineTo(x, y)
    }

    val fillPath = linePath.copy().apply {
        lineTo(linePath.getBounds().right, size.height)
        lineTo(linePath.getBounds().left, size.height)
        close()
    }

    return GraphPaths(linePath, fillPath)
}