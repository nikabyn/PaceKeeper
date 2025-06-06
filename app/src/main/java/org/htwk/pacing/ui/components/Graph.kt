package org.htwk.pacing.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// TODO Override equals and hashCode to consider array content in the method.
data class Series(val values: Array<Float>, val times: Array<Float>)

private val defaultXSteps = 4u
private val defaultYSteps = 3u
private fun defaultRange(array: Array<Float>) = array.min()..array.max()

@Composable
private fun defaultColor(): Color = if (isSystemInDarkTheme()) Color.White else Color.Black
private val defaultStroke = Stroke(width = 5.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun GraphCard(
    title: String,
    series: Series,
    xSteps: UInt = defaultXSteps,
    xRange: ClosedRange<Float> = defaultRange(series.times),
    ySteps: UInt = defaultYSteps,
    yRange: ClosedRange<Float> = defaultRange(series.values),
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        border = BorderStroke(1.dp, Color.Black),
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(text = title)
            AnnotatedGraph(series)
        }

    }
}

fun interpolate(a: Float, b: Float, t: Float): Float {
    return a + t * (b - a)
}

@Composable
fun AnnotatedGraph(
    series: Series,
    xSteps: UInt = defaultXSteps,
    xRange: ClosedRange<Float> = defaultRange(series.times),
    ySteps: UInt = defaultYSteps,
    yRange: ClosedRange<Float> = defaultRange(series.values),
    color: Color = defaultColor(),
    stroke: Stroke = defaultStroke,
    modifier: Modifier = Modifier
) {
    val yMin = yRange.start
    val yMax = yRange.endInclusive
    val yLabels = (ySteps - 1u downTo 0u).map { step ->
        val value = interpolate(yMin, yMax, step.toFloat() / (ySteps - 1u).toFloat())
        "%.1f".format(value)
    }

    val xMin = xRange.start
    val xMax = xRange.endInclusive
    val xLabels = (0u..xSteps - 1u).map { step ->
        val value = interpolate(xMin, xMax, step.toFloat() / (xSteps - 1u).toFloat())
        "%.1f".format(value)
    }

    val height = 200.dp

    Row(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .height(height)
            //.background(Color.Blue),
        ) {
            yLabels.forEach { label -> Text(label) }
        }
        Column {
            val graphPadding = 10.dp
            Graph(
                series,
                yRange = yRange,
                xRange = xRange,
                color = color,
                stroke = stroke,
                modifier = Modifier
                    // .background(Color.Green)
                    .padding(graphPadding)
                    .height(height - graphPadding * 2)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                xLabels.forEach { label -> Text(label) }
            }
        }
    }
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
        fun toGraphCoords(x: Float, y: Float): Pair<Float, Float> {
            return Pair(x * size.width, (1.0f - y) * size.height)
        }

        val path = Path()

        val start = toGraphCoords(relativeTimes.first(), relativeValues.first())
        path.moveTo(start.first, start.second)

        for ((time, value) in relativeTimes.drop(1).zip(relativeValues.drop(1))) {
            val next = toGraphCoords(time, value)
            path.lineTo(next.first, next.second)
        }

        drawPath(path, color, style = stroke)
    }
}