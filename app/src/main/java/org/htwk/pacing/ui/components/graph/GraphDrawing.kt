package org.htwk.pacing.ui.components.graph

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import kotlin.math.roundToInt

/**
 * Composable canvas optimized for graph rendering.
 *
 * Applies caching and GPU offscreen compositing for efficient redrawing.
 *
 * @param modifier Modifier for layout and styling.
 * @param onBuildDrawCache Drawing logic executed inside the [DrawScope].
 */
@Composable
fun GraphCanvas(
    modifier: Modifier = Modifier,
    onBuildDrawCache: CacheDrawScope.() -> DrawResult = {
        onDrawBehind { drawRect(color = Color.Magenta) }
    }
) {
    Box(
        modifier = modifier
            .drawWithCache(onBuildDrawCache)
            .fillMaxSize()
            .graphicsLayer() {
                // Cache the drawing as a GPU texture
                compositingStrategy = CompositingStrategy.Offscreen
                clip = true
            }
            .testTag("GraphCanvas"),
    )
}

fun Modifier.drawLines(
    steps: Int,
    lineColor: Color,
    lineWidth: Float = 2f,
): Modifier = this.drawBehind {
    if (steps < 2) return@drawBehind

    repeat(steps) { i ->
        val y = (size.height * i / (steps - 1).toFloat())
            .roundToInt()
            .toFloat()

        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = lineWidth,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 8f))
        )
    }
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
    xRange: ClosedRange<Double>,
    yRange: ClosedRange<Double>,
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