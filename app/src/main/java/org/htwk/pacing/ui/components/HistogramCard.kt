package org.htwk.pacing.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipPath
import org.htwk.pacing.ui.theme.extendedColors

/**
 * Visualizes a numeric data series (e.g., heart rate distribution) as a filled histogram,
 * overlayed with colored regions corresponding to value ranges.
 *
 * @param C The type of numeric collection holding data points (e.g., `List<Double>`).
 * @param title The title displayed at the top of the card.
 * @param series The data series to be rendered on the histogram.
 * @param zones An array of value ranges.
 * Each range defines a colored zone along the X-axis.
 * @param modifier Optional [Modifier] for styling or layout adjustments.
 *
 * ### Behavior
 * - The histogram is drawn within the coordinate range defined by [series].
 * - Each zone’s color is applied as a background band within its range.
 * - Y-axis labels are omitted (format function returns an empty string).
 *
 * ### Example
 * ```
 * HistogramCard(
 *     title = "Heart Rate Distribution",
 *     series = heartRateSeries,
 *     zonesToColorId = mapOf(
 *         (Double.NEGATIVE_INFINITY..<100.0) to R.color.zone1,
 *         (100.0..<140.0) to R.color.zone2,
 *         (140.0..<Double.POSITIVE_INFINITY) to R.color.zone3
 *     )
 * )
 * ```
 */
@Composable
fun <C : Collection<Double>> HistogramCard(
    title: String,
    series: Series<C>,
    zones: Array<OpenEndRange<Double>>,
    modifier: Modifier = Modifier,
) = CardWithTitle(title, modifier) {
    Annotation(
        series = series,
        yConfig = AxisConfig(steps = 6u, formatFunction = { "" }),
    ) { xRange, yRange ->
        val heartRateSpan = xRange.endInclusive - xRange.start
        val zonesToColors = zones.zip(
            arrayOf(
                MaterialTheme.extendedColors.cyan,
                MaterialTheme.extendedColors.green,
                MaterialTheme.extendedColors.yellow,
                MaterialTheme.extendedColors.orange,
                MaterialTheme.extendedColors.red,
            )
        )

        GraphCanvas {
            val paths = graphToPaths(series, size, xRange, yRange)

            clipPath(paths.fill) {
                for ((zone, color) in zonesToColors) {

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