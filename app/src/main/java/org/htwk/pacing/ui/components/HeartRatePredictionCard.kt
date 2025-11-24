package org.htwk.pacing.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.htwk.pacing.R
import org.htwk.pacing.ui.theme.extendedColors
import kotlin.time.Duration.Companion.hours

/**
 * Shows a graph of the last 6 hours of the users heart rate
 * and a prediction for the next 6 hours.
 * The last value in the series is used as the current time.
 */
@Composable
fun <C : Collection<Double>, D : Collection<Double>> HeartRatePredictionCard(
    title: String,
    series: Series<C>,
    seriesPredicted: Series<D>,
    yConfig: AxisConfig,
    modifier: Modifier = Modifier,
) {
    CardWithTitle(title = title, modifier) {
        if (series.x.isEmpty()) {
            Text(
                stringResource(R.string.currently_no_data_available),
                modifier = Modifier.testTag("EnergyPredictionErrorText")
            )
            return@CardWithTitle
        }

        val current = Instant.fromEpochMilliseconds(series.x.last().toLong())
        val start = (current - 6.hours).toEpochMilliseconds().toDouble()
        val end = (current + 6.hours).toEpochMilliseconds().toDouble()

        val xConfig = AxisConfig(
            range = start..end,
            formatFunction = {
                val localTime =
                    Instant.fromEpochMilliseconds(it.toLong())
                        .toLocalDateTime(TimeZone.currentSystemDefault())
                "%02d:%02d".format(localTime.hour, localTime.minute)
            }
        )

        Annotation(
            series = series,
            xConfig = xConfig,
            yConfig = yConfig,
        ) { _, yRange ->
            Row()
            {
                Graph(
                    series = series,
                    xRange = (current - 6.hours).toEpochMilliseconds()
                        .toDouble()..(current - 0.hours).toEpochMilliseconds().toDouble(),
                    yRange = yRange,
                    modifier = Modifier.weight(1f),
                )

                Graph(
                    series = seriesPredicted,
                    xRange = (current - 0.hours).toEpochMilliseconds()
                        .toDouble()..(current + 6.hours).toEpochMilliseconds().toDouble(),
                    yRange = yRange,
                    modifier = Modifier.weight(1f),
                    pathConfig = PathConfig.withStroke(MaterialTheme.extendedColors.red)
                )
            }
        }
    }
}