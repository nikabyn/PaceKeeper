package org.htwk.pacing.componets

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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

// TODO Override equals and hashCode to consider array content in the method.
data class Series(val values: Array<Float>, val times: Array<Float>)

@Composable
fun GraphCard(title: String, series: Series) {
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

fun lerp(a: Float, b: Float, t: Float): Float {
    return a + t * (b - a)
}

val DefaultColor = Color.Black
val DefaultStroke = Stroke(width = 5.0f, cap = StrokeCap.Round, join = StrokeJoin.Round)

@Composable
fun AnnotatedGraph(
    series: Series,
    color: Color = DefaultColor,
    stroke: Stroke = DefaultStroke,
    modifier: Modifier = Modifier
) {
    val ySteps = 4
    val yMax = series.values.max()
    val yMin = series.values.min()

    val yLabels = (ySteps - 1 downTo 0).map { step ->
        val value = lerp(yMin, yMax, step.toFloat() / (ySteps - 1).toFloat())
        "%.1f".format(value)
    }

    val xSteps = 6
    val xMax = series.times.max()
    val xMin = series.times.min()

    val xLabels = (0..xSteps - 1).map { step ->
        val value = lerp(xMin, xMax, step.toFloat() / (xSteps - 1).toFloat())
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
                color,
                stroke,
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
    color: Color = DefaultColor,
    stroke: Stroke = DefaultStroke,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        fun toGraphCoords(x: Float, y: Float): Pair<Float, Float> {
            return Pair(x * size.width, (1.0f - y) * size.height)
        }

        val path = Path()
        val start = toGraphCoords(series.times.first(), series.values.first())
        path.moveTo(start.first, start.second)
        for ((value, time) in series.values.drop(1).zip(series.times.drop(1))) {
            val next = toGraphCoords(time, value)
            path.lineTo(next.first, next.second)
        }

        drawPath(path, color, style = stroke)
    }
}