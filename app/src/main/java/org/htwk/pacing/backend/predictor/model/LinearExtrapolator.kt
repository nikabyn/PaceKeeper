package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor

object LinearExtrapolator {
    private val stepsIntoFuture =
        (Predictor.PREDICTION_WINDOW_DURATION.inWholeSeconds / Predictor.TIME_SERIES_STEP_DURATION.inWholeSeconds).toInt()

    data class ExtrapolationLine(
        val firstPoint: Pair<Double, Double>,
        val secondPoint: Pair<Double, Double>,
        val resultPoint: Pair<Double, Double>
    )

    private fun linearExtrapolate(x0: Double, y0: Double, x1: Double, y1: Double): Double {
        val slope = (y1 - y0) / -(x1 - x0)
        return y1 + slope * (x1 + stepsIntoFuture)
    }

    sealed interface SamplingDescriptor {
        fun getSampleResultY(timeSeries: DoubleArray): Double

        fun getSamplePositionX(): Double

        data class SinglePoint(val index: Int) : SamplingDescriptor {
            override fun getSamplePositionX(): Double {
                return index.toDouble()
            }

            override fun getSampleResultY(timeSeries: DoubleArray): Double {
                val lastIndex = timeSeries.size - 1
                return timeSeries[lastIndex - index]
            }
        }

        data class Average(val indexRange: IntRange) : SamplingDescriptor {
            override fun getSamplePositionX(): Double {
                return (indexRange.first + indexRange.last) / 2.0
            }

            override fun getSampleResultY(timeSeries: DoubleArray): Double {
                val lastIndex = timeSeries.size - 1
                return timeSeries.slice((lastIndex - indexRange.first)..(lastIndex - indexRange.last))
                    .average()
            }
        }
    }

    data class ExtrapolationStrategy(
        val samplingDescriptors: Pair<SamplingDescriptor, SamplingDescriptor>
    ) {
        fun runOnTimeSeries(timeSeries: DoubleArray): ExtrapolationLine {
            val x0 = samplingDescriptors.first.getSamplePositionX()
            val y0 = samplingDescriptors.first.getSampleResultY(timeSeries)

            val x1 = samplingDescriptors.second.getSamplePositionX()
            val y1 = samplingDescriptors.second.getSampleResultY(timeSeries)

            val result = linearExtrapolate(x0 = x0, y0 = y0, x1 = x1, y1 = y1)

            return ExtrapolationLine(
                firstPoint = x0 to y0,
                secondPoint = x1 to y1,
                resultPoint = (stepsIntoFuture + timeSeries.size).toDouble() to result
            )
        }
    }

    data class MultiExtrapolationResult(
        val extrapolations: Map<EXTRAPOLATION_STRATEGY, ExtrapolationLine>
    )

    fun multipleExtrapolate(timeSeries: DoubleArray): MultiExtrapolationResult {
        return MultiExtrapolationResult(extrapolations = EXTRAPOLATION_STRATEGY.entries.associateWith {
            it.strategy.runOnTimeSeries(timeSeries)
        })
    }

    //TODO: use fibonacci here for optimal logarithmic coverage
    //TODO: rethink shifted lines
    //TODO: use average windows per line, that correspond to trend window we're looking at
    enum class EXTRAPOLATION_STRATEGY(val strategy: ExtrapolationStrategy) {
        NOW_VS_30_MINUTES_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.SinglePoint(3),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_60_MINUTES_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.SinglePoint(6),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_90_MINUTES_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.SinglePoint(9),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_120_MINUTES_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.SinglePoint(12),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),

        HOURLY_AVG_NOW_VS_1_HOUR_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(12, 6)),
                    SamplingDescriptor.Average(IntRange(6, 0))
                )
            )
        ),
        HOURLY_AVG_NOW_VS_2_HOURS_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(18, 12)),
                    SamplingDescriptor.Average(IntRange(6, 0))
                )
            )
        ),
        HOURLY_AVG_NOW_VS_3_HOURS_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(24, 18)),
                    SamplingDescriptor.Average(IntRange(6, 0))
                )
            )
        ),
        NOW_VS_1_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(6, 0)),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_3_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(18, 0)),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_6_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(36, 0)),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_12_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(72, 0)),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        PAST_3_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(36, 18)),
                    SamplingDescriptor.Average(IntRange(18, 0))
                )
            )
        ),
        PAST_6_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(72, 36)),
                    SamplingDescriptor.Average(IntRange(36, 0))
                )
            )
        ),
        PAST_12_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(144, 72)),
                    SamplingDescriptor.Average(IntRange(72, 0))
                )
            )
        ),
        LAST_HOUR_AVERAGE_VS_1_DAY_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange(150, 144)),
                    SamplingDescriptor.Average(IntRange(6, 0))
                )
            )
        ),
        YESTERDAY_VS_TODAY(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.Average(IntRange((6 * 48 - 1), (6 * 24 - 1))),
                    SamplingDescriptor.Average(IntRange((6 * 24), 0))
                )
            )
        )
    }
}