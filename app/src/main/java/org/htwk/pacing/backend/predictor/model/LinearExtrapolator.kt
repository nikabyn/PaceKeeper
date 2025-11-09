package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor

object LinearExtrapolator {

    private val stepsIntoFuture =
        (Predictor.PREDICTION_WINDOW_DURATION.inWholeSeconds / Predictor.TIME_SERIES_STEP_DURATION.inWholeSeconds).toInt()

    data class ExtrapolationStrategy(
        val firstAveragingRange: IntRange,
        val secondAveragingRange: IntRange
    ) {
        private fun linearExtrapolate(x0: Double, y0: Double, x1: Double, y1: Double): Double {
            val slope = (y1 - y0) / -(x1 - x0)
            return y1 + slope * (x1 + stepsIntoFuture)
        }

        private fun IntRange.getAverageValueFrom(timeSeries: DoubleArray): Double {
            val lastIndex = timeSeries.size - 1
            return timeSeries.slice((lastIndex - last)..(lastIndex - first)).average()
        }

        fun runOnTimeSeries(timeSeries: DoubleArray): Double {
            val (x0, y0) = firstAveragingRange.average() to firstAveragingRange.getAverageValueFrom(
                timeSeries
            )
            val (x1, y1) = secondAveragingRange.average() to secondAveragingRange.getAverageValueFrom(
                timeSeries
            )

            val result = linearExtrapolate(x0 = x0, y0 = y0, x1 = x1, y1 = y1)
            println("($x0 $y0) ($x1 $y1) : $result")
            return result
        }
    }

    //TODO: use fibonacci here for optimal logarithmic coverage
    //TODO: rethink shifted lines
    //TODO: use average windows per line, that correspond to trend window we're looking at
    enum class EXTRAPOLATION_STRATEGY(val strategy: ExtrapolationStrategy) {
        NOW_VS_30_MINUTES_AGO(
            ExtrapolationStrategy(
                IntRange(3, 3),
                IntRange(0, 0)
            )
        ),
        NOW_VS_60_MINUTES_AGO(
            ExtrapolationStrategy(
                IntRange(6, 6),
                IntRange(0, 0)
            )
        ),
        NOW_VS_90_MINUTES_AGO(
            ExtrapolationStrategy(
                IntRange(9, 9),
                IntRange(0, 0)
            )
        ),
        NOW_VS_120_MINUTES_AGO(
            ExtrapolationStrategy(
                IntRange(12, 12),
                IntRange(0, 0)
            )
        ),

        HOURLY_AVG_NOW_VS_1_HOUR_AGO(
            ExtrapolationStrategy(
                IntRange((6 * 2), 6),
                IntRange(6, 0)
            )
        ),
        HOURLY_AVG_NOW_VS_2_HOURS_AGO(
            ExtrapolationStrategy(
                IntRange((6 * 3), (6 * 2)),
                IntRange(6, 0)
            )
        ),
        HOURLY_AVG_NOW_VS_3_HOURS_AGO(
            ExtrapolationStrategy(
                IntRange((6 * 4), (6 * 3)),
                IntRange(6, 0)
            )
        ),
        NOW_VS_1_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange((6 * 1), 0),
                IntRange(0, 0)
            )
        ),
        NOW_VS_3_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange((6 * 3), 0),
                IntRange(0, 0)
            )
        ),
        NOW_VS_6_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange((6 * 6), 0),
                IntRange(0, 0)
            )
        ),
        NOW_VS_12_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange((6 * 12), 0),
                IntRange(0, 0)
            )
        ),
        PAST_3_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange((6 * 6), (6 * 3)),
                IntRange((6 * 3), 0)
            )
        ),
        PAST_6_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange((6 * 12), (6 * 6)),
                IntRange((6 * 6), 0)
            )
        ),
        PAST_12_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange((6 * 24), (6 * 12)),
                IntRange((6 * 12), 0)
            )
        ),
        LAST_HOUR_AVERAGE_VS_1_DAY_AGO(
            ExtrapolationStrategy(
                IntRange((6 * 25), (6 * 24)),
                IntRange((6 * 1), 0)
            )
        ),
        YESTERDAY_VS_TODAY(
            ExtrapolationStrategy(
                IntRange((6 * 48), (6 * 24)),
                IntRange((6 * 24), 0)
            )
        )
    }

    data class MultiExtrapolationResult(
        val extrapolations: Map<EXTRAPOLATION_STRATEGY, Double>
    )

    fun multipleExtrapolate(timeSeries: DoubleArray): MultiExtrapolationResult {
        return MultiExtrapolationResult(extrapolations = EXTRAPOLATION_STRATEGY.entries.associateWith {
            print(it.toString())
            it.strategy.runOnTimeSeries(timeSeries)
        })
    }
}