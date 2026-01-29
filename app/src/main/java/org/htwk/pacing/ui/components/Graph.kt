package org.htwk.pacing.ui.components

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
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.DrawResult
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
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
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.constraintlayout.compose.Dimension
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.ui.math.interpolate
import org.htwk.pacing.ui.theme.Spacing
import kotlin.math.roundToInt

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
fun GraphCard(
    title: String,
    xData: List<Double>,
    yData: List<Double>,
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
            xData = xData,
            yData = yData,
            xConfig = xConfig,
            yConfig = yConfig,
            pathConfig = pathConfig,
        )
    }
}

@Composable
fun GraphLayout(
    xLabels: @Composable () -> Unit,
    yLabels: @Composable () -> Unit,
    graph: @Composable () -> Unit,
) = ConstraintLayout(
    modifier = Modifier
        .fillMaxSize()
        .padding(horizontal = Spacing.large, vertical = Spacing.largeIncreased)
) {
    val (yAxis, graph, xAxis) = createRefs()

    Axis(
        horizontal = false,
        modifier = Modifier
            .constrainAs(yAxis) {
                start.linkTo(parent.start)
                top.linkTo(parent.top)
                end.linkTo(graph.start)
                bottom.linkTo(graph.bottom)
                height = Dimension.fillToConstraints
            }
    ) {
        yLabels()
    }

    Box(
        modifier = Modifier.constrainAs(graph) {
            top.linkTo(parent.top)
            bottom.linkTo(xAxis.top)
            start.linkTo(yAxis.end)
            end.linkTo(parent.end)
            height = Dimension.fillToConstraints
            width = Dimension.fillToConstraints
        }
    ) {
        graph()
    }

    Axis(
        horizontal = true,
        modifier = Modifier
            .constrainAs(xAxis) {
                start.linkTo(graph.start)
                top.linkTo(graph.bottom)
                end.linkTo(graph.end)
                bottom.linkTo(parent.bottom)
                width = Dimension.fillToConstraints
            }
    ) {
        xLabels()
    }
}

/**
 * A line graph with two labelled axes.
 *
 * User must set Modifier.height(...)!
 */
@Composable
fun AnnotatedGraph(
    xData: List<Double>,
    yData: List<Double>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    pathConfig: PathConfig = PathConfig.withStroke(),
) {
    Annotation(xData, yData, modifier, xConfig, yConfig) { xRange, yRange ->
        Graph(
            xData = xData,
            yData = yData,
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
fun Annotation(
    xData: List<Double>,
    yData: List<Double>,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    slot: @Composable ((yRange: ClosedRange<Double>, xRange: ClosedRange<Double>) -> Unit) =
        { _, _ -> Box(modifier = Modifier.fillMaxSize()) { } },
) {
    val xRange = xConfig.range ?: Graph.defaultRange(xData)
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

    val yRange = yConfig.range ?: Graph.defaultRange(yData)
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
            val horizontalPadding = if (xSteps == 0u) 0.dp else Spacing.small
            val paddingTop = if (ySteps == 0u) 0.dp else Spacing.small
            Box(
                modifier = Modifier
                    .padding(
                        start = horizontalPadding,
                        top = paddingTop,
                        end = horizontalPadding,
                        bottom = Spacing.small,
                    )
                    .weight(1f)
                    .drawLines(ySteps.toInt())
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

@Composable
fun AxisLabelHourMinutes(time: Instant) {
    val localTime = time.toLocalDateTime(TimeZone.currentSystemDefault())
    val text = "%02d:%02d".format(localTime.hour, localTime.minute)
    AxisLabel(text)
}

@Composable
fun AxisLabel(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    modifier = Modifier.testTag("AxisLabel")
)

@Composable
fun Axis(
    horizontal: Boolean,
    modifier: Modifier = Modifier,
    labels: @Composable () -> Unit,
) =
    if (horizontal) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = modifier.fillMaxWidth(),
        ) { labels() }
    } else {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
            modifier = modifier.fillMaxHeight(),
        ) { labels() }
    }


fun Modifier.drawLines(ySteps: Int): Modifier = this.drawBehind {
    if (ySteps < 2) return@drawBehind

    val strokeWidth = 2f

    repeat(ySteps) { i ->
        val y = (size.height * i / (ySteps - 1).toFloat())
            .roundToInt()
            .toFloat()

        drawLine(
            color = Color.Gray,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 8f))
        )
    }
}

/**
 * Draws a simple line graph for the given numeric [series].
 *
 * The graph supports both stroke and fill rendering through [pathConfig].
 * A height must be set with `Modifier.height(...)`.
 *
 * @param xData The data for the x axis.
 * @param yData The data for the y axis.
 * @param modifier Modifier for layout and styling.
 * @param xRange Range of X-axis values to display. Defaults to the min–max of [xData].
 * @param yRange Range of Y-axis values to display. Defaults to the min–max of [yData].
 * @param pathConfig Configuration for stroke and fill styles.
 */
@Composable
fun Graph(
    xData: List<Double>,
    yData: List<Double>,
    modifier: Modifier = Modifier,
    xRange: ClosedRange<Double> = Graph.defaultRange(xData),
    yRange: ClosedRange<Double> = Graph.defaultRange(yData),
    pathConfig: PathConfig = PathConfig.withStroke(),
) {
    val fillColor = pathConfig.fill ?: Graph.defaultFillColor()
    val strokeColor = pathConfig.color ?: Graph.defaultStrokeColor()
    val strokeStyle = pathConfig.style ?: Graph.defaultStrokeStyle()

    GraphCanvas(modifier) {
        val paths = graphToPaths(xData, yData, size, xRange, yRange)

        onDrawBehind {
            if (pathConfig.hasFill) {
                drawPath(paths.fill, fillColor)
            }

            if (pathConfig.hasStroke) {
                drawPath(paths.line, strokeColor, style = strokeStyle)
            }
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

    fun defaultStrokeStyle() = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)

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
 * @param onCachedDraw Drawing logic executed inside the [DrawScope].
 */
@Composable
fun GraphCanvas(
    modifier: Modifier = Modifier,
    onCachedDraw: CacheDrawScope.() -> DrawResult = {
        onDrawBehind { drawRect(color = Color.Magenta) }
    }
) {
    Box(
        modifier = modifier
            .drawWithCache(onCachedDraw)
            .fillMaxSize()
            .graphicsLayer() {
                // Cache the drawing as a GPU texture
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .testTag("GraphCanvas"),
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
 * Converts numeric data into drawable [Path] objects representing
 * the line and filled area of a graph.
 *
 * @param xData The data for the x axis.
 * @param yData The data for the y axis.
 * @param size The canvas size for coordinate mapping.
 * @param xRange The visible X-axis range.
 * @param yRange The visible Y-axis range.
 * @return A [GraphPaths] object containing line and fill paths.
 */
fun graphToPaths(
    xData: List<Double>,
    yData: List<Double>,
    size: Size,
    xRange: ClosedRange<Double> = Graph.defaultRange(xData),
    yRange: ClosedRange<Double> = Graph.defaultRange(yData),
): GraphPaths {
    val linePath = Path()

    if (xData.isEmpty() || yData.isEmpty()) {
        return GraphPaths(linePath, Path())
    }

    val posX = xData.map { xValue ->
        (((xValue - xRange.start) / (xRange.endInclusive - xRange.start)).toFloat() * size.width)
    }
    val posY = yData.map { yValue ->
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