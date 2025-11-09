package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor

object TimeSeriesDiscretizer {
    /**
     * Converts an [Instant] to a discrete time step.
     * @param instant The [Instant] to discretize.
     * @return The discrete time step as a [ULong].
     */
    private fun discretizeInstant(instant: Instant): Long {
        val stepTime =
            instant.toEpochMilliseconds() / Predictor.TIME_SERIES_STEP_DURATION.inWholeMilliseconds
        return stepTime.toLong()
    }

    /**
     * Calculates the average value for each time bucket from a list of timed data points.
     * Depending on the [IPreprocessor.GenericTimeSeriesEntries.type], it either calculates the average
     * (for continuous data) or the sum (for aggregated data) for each bucket.
     * @param input The generic time series data to be processed.
     * @return A map where keys are discrete time buckets (relative to startTime) and values are the averaged data points.
     */
    private fun calculateTimeBucketAverages(
        input: IPreprocessor.GenericTimeSeriesEntries
    ): Map<Int, Double> {
        val timeStart = input.timeStart
        val entries = input.data
        val timeSeriesType = input.type

        require(entries.isNotEmpty()) { "Input entries list cannot be empty." }
        require(entries.minOf { it.time } >= timeStart) { "All entry times must be at or after the start time." }

        val startTimeDiscrete: Long = discretizeInstant(timeStart)

        //sort into discrete time step buckets and calculate average bucket
        val timeBucketGroups = entries.groupBy { it ->
            //the absolute step value might be larger than 32-bit integer for small step sizes, hence calculate difference in 64 bit arithmetic
            //the difference itself can fit into a 32 bit integer, that we later use for indexing
            (discretizeInstant(it.time) - startTimeDiscrete).toInt()
        }

        val timeBucketValues = timeBucketGroups.mapValues { (_, group) ->
            //for aggregated values (like steps) we don't want averages, but sums
            if (timeSeriesType == IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType.AGGREGATED) {
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
        timeBucketAverages: Map<Int, Double>,
        timeSeriesType: IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType
    ): DoubleArray {
        require(timeBucketAverages.isNotEmpty())
        val sortedBucketAverages = timeBucketAverages.toSortedMap()

        //optionally pin the first and last known value to the borders so that we don't get missing
        //values,this will lead to constant extrapolation at the edges
        //only makes sense with continuous time series(bpm), not with aggregated ones (like steps)
        val isContinuousTimeSeries =
            (timeSeriesType == IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType.CONTINUOUS)
        if (isContinuousTimeSeries) {
            val firstValue = sortedBucketAverages.getValue(sortedBucketAverages.firstKey())
            val lastValue = sortedBucketAverages.getValue(sortedBucketAverages.lastKey())
            val lastKey = (Predictor.TIME_SERIES_SAMPLE_COUNT - 1)

            sortedBucketAverages.putIfAbsent(0, firstValue)
            sortedBucketAverages.putIfAbsent(lastKey, lastValue);
        }

        val sortedBucketList = sortedBucketAverages.toList()

        //resulting time series, with interpolated, discrete values
        val discreteTimeSeries = DoubleArray(Predictor.TIME_SERIES_SAMPLE_COUNT) { 0.0 }

        //fill known points
        for ((timeStep, value) in sortedBucketAverages) {
            val index = timeStep.toInt()
            if (index in discreteTimeSeries.indices) {
                discreteTimeSeries[index] = value
            }
        }

        //we don't want interpolation on time series like distance/steps that are aggregated
        if (!isContinuousTimeSeries) return discreteTimeSeries

        //add interpolation steps between those points
        for ((startPoint, endPoint) in sortedBucketList.zipWithNext()) {
            val (x0, y0) = startPoint
            val (x1, y1) = endPoint

            val intervalSteps = (x1 - x0).toInt()
            if (intervalSteps > 1) {
                val slope = (y1 - y0) / intervalSteps.toDouble()
                for (i in 1 until intervalSteps) {
                    val index = x0 + i
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
        input: IPreprocessor.GenericTimeSeriesEntries
    ): DoubleArray {
        val timeBucketAverages = calculateTimeBucketAverages(input)

        val discreteTimeSeries = discretizeWithMissingValues(timeBucketAverages, input.type)

        return discreteTimeSeries
    }
}