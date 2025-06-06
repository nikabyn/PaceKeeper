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
import org.htwk.pacing.math.interpolate
import kotlin.math.abs

// TODO Override equals and hashCode to consider array content in the method.
data class Series(val values: Array<Float>, val times: Array<Float>)

private fun defaultRange(array: Array<Float>): ClosedRange<Float> {
    val min = array.minOrNull() ?: 0.0f
    val max = array.maxOrNull() ?: 0.0f
    return min..max
}

@Composable
private fun defaultColor(): Color = if (isSystemInDarkTheme()) Color.White else Color.Black
private val defaultStroke = Stroke(width = 5.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun GraphCard(
    title: String,
    series: Series,
    modifier: Modifier = Modifier,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
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
            )
        }
    }
}

data class AxisConfig(
    val range: ClosedRange<Float>? = null,
    val steps: UInt? = null,
    val formatFunction: (value: Float) -> String = { value -> "%.1f".format(value) }
)

@Composable
fun AnnotatedGraph(
    series: Series,
    xConfig: AxisConfig = AxisConfig(),
    yConfig: AxisConfig = AxisConfig(),
    color: Color = defaultColor(),
    stroke: Stroke = defaultStroke,
    modifier: Modifier = Modifier,
) {
    val xRange = xConfig.range ?: defaultRange(series.times)
    val xSteps = xConfig.steps ?: 3u;
    val xLabels = (0u..<xSteps).map { step ->
        val value =
            interpolate(xRange.start, xRange.endInclusive, step.toFloat() / (xSteps - 1u).toFloat())
        xConfig.formatFunction(value)
    }

    val yRange = yConfig.range ?: defaultRange(series.values)
    val ySteps = yConfig.steps ?: 3u;
    val yLabels = (ySteps - 1u downTo 0u).map { step ->
        val value =
            interpolate(yRange.start, yRange.endInclusive, step.toFloat() / (ySteps - 1u).toFloat())
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
                color = color,
                stroke = stroke,
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

@Composable
fun Graph(
    series: Series,
    xRange: ClosedRange<Float> = defaultRange(series.times),
    yRange: ClosedRange<Float> = defaultRange(series.values),
    color: Color = defaultColor(),
    stroke: Stroke = defaultStroke,
    modifier: Modifier = Modifier
) {
    val relativeValues = series.values.map { value ->
        (value - yRange.start) / abs(yRange.endInclusive - yRange.start)
    }
    val relativeTimes = series.times.map { time ->
        (time - xRange.start) / abs(xRange.endInclusive - xRange.start)
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        fun toGraphCoords(x: Float, y: Float) = Pair(x * size.width, (1.0f - y) * size.height)

        val path = Path()

        if (series.times.isNotEmpty() || series.values.isNotEmpty()) {
            val start = toGraphCoords(relativeTimes.first(), relativeValues.first())
            path.moveTo(start.first, start.second)
        }

        for ((time, value) in relativeTimes.drop(1).zip(relativeValues.drop(1))) {
            val next = toGraphCoords(time, value)
            path.lineTo(next.first, next.second)
        }

        drawPath(path, color, style = stroke)
    }
}