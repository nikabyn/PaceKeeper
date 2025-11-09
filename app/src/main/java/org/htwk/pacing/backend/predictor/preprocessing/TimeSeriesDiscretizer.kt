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
        isAggregation: Boolean
    ): Map<ULong, Double> {
        require(entries.isNotEmpty()) { "Input entries list cannot be empty." }
        require(entries.minOf { it.time } >= startTime) { "All entry times must be at or after the start time." }

        val startTimeDiscrete: ULong = discretizeInstant(startTime)

        //sort into discrete time step buckets and calculate average bucket
        val timeBucketGroups = entries.groupBy { it ->
            discretizeInstant(it.time) - startTimeDiscrete
        }

        val timeBucketValues = timeBucketGroups.mapValues { (_, group) ->
            //for aggregated values (like steps) we don't want averages, but sums
            if (isAggregation) {
                group.sumOf { it -> it.value }
            } else {
                // TODO: weighted resampling/average, because incoming HR data points are probably unevenly spaced
                group.map { it -> it.value }.average()
            }
        }
        return timeBucketValues
    }

    /**
     * Fills in missing values in a discretized time series using linear interpolation.
     * @param timeBucketAverages A map of discrete time buckets to their average values.
     * @param doEdgeExtrapolation Whether to extrapolate the first and last known values.
     * @return A [DoubleArray] representing the complete, interpolated time series.
     */
    private fun discretizeWithMissingValues(
        timeBucketAverages: Map<ULong, Double>,
        isAggregation: Boolean
    ): DoubleArray {
        require(timeBucketAverages.isNotEmpty())
        val sortedBucketAverages = timeBucketAverages.toSortedMap()

        //optionally pin the first and last known value to the borders so that we don't get missing
        //values,this will lead to constant extrapolation at the edges
        //only makes sense with continuous time series(bpm), not with aggregated ones (like steps)
        val doEdgeExtrapolation = !isAggregation
        if (doEdgeExtrapolation) {
            val firstValue = sortedBucketAverages.getValue(sortedBucketAverages.firstKey())
            val lastValue = sortedBucketAverages.getValue(sortedBucketAverages.lastKey())
            val lastKey = (Predictor.TIME_SERIES_SAMPLE_COUNT - 1).toULong()

            sortedBucketAverages.putIfAbsent(0u, firstValue)
            sortedBucketAverages.putIfAbsent(lastKey, lastValue);
        }

        val sortedBucketList = sortedBucketAverages.toList()

        //resulting time series, with interpolated, discrete values
        val discreteTimeSeries = DoubleArray(Predictor.TIME_SERIES_SAMPLE_COUNT) { Double.NaN }

        //fill known points
        for ((timeStep, value) in sortedBucketAverages) {
            val index = timeStep.toInt()
            if (index in discreteTimeSeries.indices) {
                discreteTimeSeries[index] = value
            }
        }

        //add interpolation steps between those points
        for ((startPoint, endPoint) in sortedBucketList.zipWithNext()) {
            val (x0, y0) = startPoint
            val (x1, y1) = endPoint

            val intervalSteps = (x1 - x0).toInt()
            if (intervalSteps > 1) {
                val slope = (y1 - y0) / intervalSteps.toDouble()
                for (i in 1 until intervalSteps) {
                    val index = x0.toInt() + i
                    if (index in discreteTimeSeries.indices) {
                        discreteTimeSeries[index] = y0 + slope * i
                    }
                }
            }
        }

        return discreteTimeSeries
    }

    /**
     * Discretizes a list of timed data points into a fixed-size time series array.
     * @param timeStart The start time for the discretization.
     * @param input The list of [GenericTimedDataPoint]s to process.
     * @param isAggregation True if the input data corresponds to aggregated values like steps,
     *  for such data we should take sums instead of averages in an interval,
     *  since steps in a time interval accumulate, they don't average
     * @return A [DoubleArray] representing the discretized time series.
     */
    fun discretizeTimeSeries(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
        isAggregation: Boolean
    ): DoubleArray {
        val timeBucketAverages = calculateTimeBucketAverages(timeStart, input, isAggregation)

        val discreteTimeSeries = discretizeWithMissingValues(timeBucketAverages, isAggregation)

        return discreteTimeSeries
    }
}