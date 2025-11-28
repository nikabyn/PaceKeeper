package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.slice
import org.jetbrains.kotlinx.multik.ndarray.operations.average

object LinearExtrapolator {
    private val stepsIntoFuture = Predictor.PREDICTION_WINDOW_SAMPLE_COUNT

    /**
     * Represents the outcome of a linear extrapolation.
     *
     * This data class holds the two points from the time series that were used to define the trend line,
     * and the resulting extrapolated point in the future. Each point is a pair of (x, y) coordinates,
     * where 'x' represents time (as an index) and 'y' represents the sampled value.
     *
     * @property firstPoint The first point (x0, y0) used for extrapolation, typically the older point in time.
     * @property secondPoint The second point (x1, y1) used for extrapolation, typically the more recent point.
     * @property resultPoint The extrapolated point (x_future, y_future) representing the prediction.
     */
    data class ExtrapolationLine(
        val firstPoint: Pair<Double, Double>,
        val secondPoint: Pair<Double, Double>,
        val resultPoint: Pair<Double, Double>
    ) {
        fun getExtrapolationResult(): Double {
            return resultPoint.second
        }
    }

    /**
     * Extrapolates a future value based on a linear trend defined by two points.
     *
     * The extrapolation target is a fixed number of steps into the future, defined by `stepsIntoFuture`.
     *
     * @param x0 The time index of the older point (a larger number).
     * @param y0 The value at time `x0`.
     * @param x1 The time index of the more recent point (a smaller number).
     * @param y1 The value at time `x1`.
     * @return The extrapolated `y` value at x = now + stepsIntoFuture
     */
    private fun linearExtrapolate(x0: Double, y0: Double, x1: Double, y1: Double): Double {
        //flip the sign, because the indices are counted in reverse time
        //e.g.: x0 > x1 when in reality, x0 refers to a timepoint that comes before x1
        val slope = (y1 - y0) / -(x1 - x0)
        return y1 + slope * (x1 + stepsIntoFuture)
    }

    /**
     * Describes how to sample a single data point from a time series.
     * This is used to define the two points required for linear extrapolation.
     * The sampling can be a single data point at a specific index or an average over a range of indices.
     *
     * All indices are relative to the end of the time series. An index of 0 represents the most recent data point.
     */
    sealed interface SamplingDescriptor {
        val validRange: IntRange
            get() = 0..<(Predictor.TIME_SERIES_DURATION / Predictor.TIME_SERIES_STEP_DURATION).toInt()

        companion object {
            var indexOffset: Int = 0
        }

        /**
         * Retrieves a sample value (the Y-coordinate) from the given time series.
         *
         * The exact method of sampling depends on the implementing class. It can be a single data point
         * at a specific index or an average over a range of indices. The indices are calculated
         * relative to the end of the `timeSeries` array.
         *
         * @param timeSeries The historical data array from which to sample. It's assumed to be sorted chronologically,
         *                   with the most recent data point at the end of the array.
         * @return The calculated sample value (Y-coordinate).
         */
        fun getSampleResultY(timeSeries: D1Array<Double>): Double

        /**
         * Gets the X-axis coordinate (time position) for this sample.
         * The position is represented as an index relative to the end of the time series.
         * For a single point, this is simply its index. For a range, it's the midpoint of the range.
         * This value is used as one of the X-coordinates for constructing the extrapolation line.
         *
         * @return The X-coordinate as a [Double].
         */
        fun getSamplePositionX(): Double

        /**
         * A [SamplingDescriptor] that represents a single data point from the time series.
         *
         * This is used to define one of the two points needed for linear extrapolation.
         * The point is identified by its index relative to the end of the time series data.
         *
         * @property index The index of the data point to sample, relative to the most recent entry.
         *                 `0` represents the most recent data point, `1` represents the one before that, and so on.
         */
        data class SinglePoint(val index: Int) : SamplingDescriptor {
            init {
                require(index in validRange) { "earliestIndex must be in $validRange, but was $index" }
            }

            override fun getSamplePositionX(): Double {
                return index.toDouble()
            }

            override fun getSampleResultY(timeSeries: D1Array<Double>): Double {
                val lastIndex = validRange.last
                return timeSeries[lastIndex - index + indexOffset]
            }
        }

        /**
         * A [SamplingDescriptor] that represents a sample point calculated by averaging a slice of the time series.
         * The slice is defined by a range of indices relative to the end of the time series.
         *
         * For example, `AverageOverRange(6, 0)` would take the average of the last 7 data points (from index `end-6` to `end-0`).
         * The x-position of this sample is the midpoint of the index range.
         *
         * @property earliestIndex The starting index of the slice, relative to the end of the time series. Must be greater than or equal to `latestIndex`.
         * @property latestIndex The ending index of the slice, relative to the end of the time series. 0 represents the most recent data point.
         */
        data class AverageOverRange(val earliestIndex: Int, val latestIndex: Int) :
            SamplingDescriptor {
            init {
                require(earliestIndex in validRange) { "earliestIndex must be in $validRange, but was $earliestIndex" }
                require(latestIndex in validRange) { "latestIndex must be in $validRange, but was $latestIndex" }
                require(earliestIndex >= latestIndex) { "earliestIndex must be greater than or equal to latestIndex" }
            }

            override fun getSamplePositionX(): Double {
                return (earliestIndex + latestIndex) / 2.0
            }

            override fun getSampleResultY(timeSeries: D1Array<Double>): Double {
                val lastIndex = validRange.last
                return timeSeries.slice<Double, D1, D1>(
                    inSlice = (lastIndex - earliestIndex + indexOffset)..(lastIndex - latestIndex + indexOffset),
                    axis = 0
                ).average<Double, D1>()
            }
        }
    }

    /**
     * Defines a strategy for linear extrapolation based on two sampling points from a time series.
     *
     * This class encapsulates the logic of how to pick two points (x0, y0) and (x1, y1) from a given
     * time series. These points are then used to construct a line and extrapolate a future value.
     * The way these points are chosen is defined by the [samplingDescriptors]. For example, a point
     * could be a single value from the time series, or an average over a range of values.
     *
     * @property samplingDescriptors A pair of [SamplingDescriptor]s that define how to extract the two
     *   points (the 'first' and 'second' points) from the time series data for extrapolation.
     *   The first descriptor defines the older point in time (x0, y0), and the second descriptor
     *   defines the more recent point (x1, y1).
     */
    data class ExtrapolationStrategy(
        val samplingDescriptors: Pair<SamplingDescriptor, SamplingDescriptor>
    ) {
        /**
         * Executes the extrapolation strategy on a given time series.
         *
         * This function uses a pair of `samplingDescriptors` to extract/sample two points (x0, y0) and (x1, y1)
         * from the `timeSeries` data. It then linearly extrapolates a future value from the trend line through those two points.
         * The result is returned as an [ExtrapolationLine], which contains the two original points
         * used for the calculation and the final extrapolated point.
         *
         * @param timeSeries The time series array to perform the extrapolation on. The array is
         *                   expected to contain chronologically ordered `Double` values, with the
         *                   most recent value at the end.
         * @return An [ExtrapolationLine] object containing the two sampled points and the resulting
         *         extrapolated point.
         */
        fun runOnTimeSeries(timeSeries: D1Array<Double>): ExtrapolationLine {
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

    /**
     * Holds a map of extrapolation strategies to their calculated [ExtrapolationLine] results.
     *
     * @property extrapolations A map from an [EXTRAPOLATION_STRATEGY] to its resulting prediction line.
     */
    data class MultiExtrapolationResult(
        val extrapolations: Map<EXTRAPOLATION_STRATEGY, ExtrapolationLine>
    )

    fun multipleExtrapolate(
        timeSeries: D1Array<Double>,
        indexOffset: Int = 0
    ): MultiExtrapolationResult {
        SamplingDescriptor.indexOffset = indexOffset

        return MultiExtrapolationResult(extrapolations = EXTRAPOLATION_STRATEGY.entries.associateWith {
            it.strategy.runOnTimeSeries(timeSeries)
        })
    }

    //IDEA: use fibonacci here for optimal logarithmic coverage, add time-shifted point trend lines
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
                    SamplingDescriptor.AverageOverRange(12, 6),
                    SamplingDescriptor.AverageOverRange(6, 0)
                )
            )
        ),
        HOURLY_AVG_NOW_VS_2_HOURS_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(18, 12),
                    SamplingDescriptor.AverageOverRange(6, 0)
                )
            )
        ),
        HOURLY_AVG_NOW_VS_3_HOURS_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(24, 18),
                    SamplingDescriptor.AverageOverRange(6, 0)
                )
            )
        ),
        NOW_VS_1_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(6, 0),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_3_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(18, 0),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_6_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(36, 0),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        NOW_VS_12_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(72, 0),
                    SamplingDescriptor.SinglePoint(0)
                )
            )
        ),
        PAST_3_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(36, 18),
                    SamplingDescriptor.AverageOverRange(18, 0)
                )
            )
        ),
        PAST_6_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(72, 36),
                    SamplingDescriptor.AverageOverRange(36, 0)
                )
            )
        ),
        PAST_12_HOUR_TREND(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(144, 72),
                    SamplingDescriptor.AverageOverRange(72, 0)
                )
            )
        ),
        LAST_HOUR_AVERAGE_VS_1_DAY_AGO(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange(150, 144),
                    SamplingDescriptor.AverageOverRange(6, 0)
                )
            )
        ),
        YESTERDAY_VS_TODAY(
            ExtrapolationStrategy(
                Pair(
                    SamplingDescriptor.AverageOverRange((6 * 48 - 1), (6 * 24 - 1)),
                    SamplingDescriptor.AverageOverRange((6 * 24), 0)
                )
            )
        )
    }
}