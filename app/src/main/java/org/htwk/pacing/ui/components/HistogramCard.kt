package org.htwk.pacing.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.colorResource

@Composable
fun <C : Collection<Double>> HistogramCard(
    title: String,
    series: Series<C>,
    zonesToColorId: Map<OpenEndRange<Double>, Int>,
    modifier: Modifier = Modifier,
) = CardWithTitle(title, modifier) {
    Annotation(
        series = series,
        yConfig = AxisConfig(steps = 5u, formatFunction = { "" }),
    ) { xRange, yRange ->
        val heartRateSpan = xRange.endInclusive - xRange.start
        val zonesToColor = zonesToColorId.mapValues { value ->
            colorResource(value.value)
        }

        GraphCanvas {
            val paths = graphToPaths(series, size, xRange, yRange)

            clipPath(paths.fill) {
                for ((zone, color) in zonesToColor) {

                    val startEdge =
                        ((zone.start - xRange.start) / heartRateSpan)
                            .coerceIn(0.0, 1.0)
                            .toFloat()
                    val endEdge =
                        ((zone.endExclusive - xRange.start) / heartRateSpan)
                            .coerceIn(0.0, 1.0)
                            .toFloat()

                    drawRect(
                        color = color,
                        topLeft = Offset(startEdge * size.width, 0f),
                        size = Size(
                            width = (endEdge * size.width) - (startEdge * size.width),
                            height = size.height
                        )
                    )
                }
            }
        }
    }
}