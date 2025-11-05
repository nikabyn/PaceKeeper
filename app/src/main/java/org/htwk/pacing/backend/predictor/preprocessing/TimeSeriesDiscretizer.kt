package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor

object TimeSeriesDiscretizer {
    /**
     * Converts an [Instant] to a discrete time step.
     * @param instant The [Instant] to discretize.
     * @return The discrete time step as a [ULong].
     */
    private fun discretizeInstant(instant: Instant): ULong {
        val stepTime =
            instant.toEpochMilliseconds() / Predictor.TIME_SERIES_STEP_DURATION.inWholeMilliseconds
        return stepTime.toULong()
    }

    /**
     * Calculates the average value for each time bucket from a list of timed data points.
     * @param startTime The start time of the time series.
     * @param entries A list of [GenericTimedDataPoint]s to be processed.
     * @return A map where keys are discrete time buckets (relative to startTime) and values are the averaged data points.
     * @throws IllegalArgumentException if the entries list is empty or contains timestamps before startTime.
     */
    private fun calculateTimeBucketAverages(
        startTime: Instant,
        entries: List<GenericTimedDataPoint>,
    ): Map<ULong, Double> {
        require(entries.isNotEmpty()) { "Input entries list cannot be empty." }
        require(entries.minOf { it.time } >= startTime) { "All entry times must be at or after the start time." }

        val startTimeDiscrete: ULong = discretizeInstant(startTime)

        //sort into discrete time step buckets and calculate average bucket
        val timeBucketAverages: Map<ULong, Double> = entries.groupBy { it ->
            //group values into buckets based on which time step they belong to
            discretizeInstant(it.time) - startTimeDiscrete
        }.mapValues { (_, group) ->
            //map each group to an average value
            // TODO: weighted resampling/average, because incoming HR data points are probably unevenly spaced
            group.map { it -> it.value }.average()
        }

        return timeBucketAverages
    }

    /**
     * Fills in missing values in a discretized time series using linear interpolation.
     * @param timeBucketAverages A map of discrete time buckets to their average values.
     * @return A [DoubleArray] representing the complete, interpolated time series.
     */
    private fun discretizeWithMissingValues(
        timeBucketAverages: Map<ULong, Double>,
    ): DoubleArray {
        val sortedAverages = timeBucketAverages.toSortedMap()

        //ensure value at t=0 (for constant extrapolation at edges if missing)
        if (sortedAverages.firstKey() != 0UL) {
            sortedAverages[0UL] = sortedAverages.getValue(sortedAverages.firstKey())
        }

        //ensure value at t=end (for constant extrapolation at edges if missing)
        if (sortedAverages.lastKey() != Predictor.TIME_SERIES_SAMPLE_COUNT.toULong()) {
            sortedAverages[Predictor.TIME_SERIES_SAMPLE_COUNT.toULong()] =
                sortedAverages.getValue(sortedAverages.lastKey())
        }

        //resulting time series, with interpolated, discrete values
        val discreteTimeSeries = DoubleArray(Predictor.TIME_SERIES_SAMPLE_COUNT) { Double.NaN }

        timeBucketAverages.entries.sortedBy { it.key }.zipWithNext()
            .forEach { (startPoint, endPoint) ->
                val (x0, y0) = startPoint
                val (x1, y1) = endPoint

                val intervalSteps = (x1 - x0).toInt()
                if (intervalSteps > 1) {
                    val slope = (y1 - y0) / intervalSteps.toDouble()
                    for (i in 1 until intervalSteps) {
                        discreteTimeSeries[i] = y0 + slope * i
                    }
                }
            }

        return discreteTimeSeries
    }

    /**
     * Discretizes a list of timed data points into a fixed-size time series array.
     * @param timeStart The start time for the discretization.
     * @param input The list of [GenericTimedDataPoint]s to process.
     * @return A [DoubleArray] representing the discretized time series.
     */

    fun discretizeTimeSeries(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
    ): DoubleArray {
        val timeBucketAverages = calculateTimeBucketAverages(timeStart, input)
        val discreteTimeSeries = discretizeWithMissingValues(timeBucketAverages)

        return discreteTimeSeries
    }
}