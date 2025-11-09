package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import java.util.SortedMap

object TimeSeriesDiscretizer {
    /**
     * Converts an [Instant] to a discrete time step.
     * @param instant The [Instant] to discretize.
     * @return The discrete time step as a [ULong].
     */
    private fun discretizeInstant(instant: Instant): Long {
        return instant.toEpochMilliseconds() / Predictor.TIME_SERIES_STEP_DURATION.inWholeMilliseconds
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
    ): SortedMap<Int, Double> {
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
            when (timeSeriesType) {
                IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType.AGGREGATED ->
                    group.sumOf { it.value }

                IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType.CONTINUOUS ->
                    group.map { it.value }
                        .average() // TODO: weighted resampling/average, because incoming HR data points are probably unevenly spaced
            }
        }

        return timeBucketValues.toSortedMap()
    }

    /**
     * Converts a sorted list of time buckets into a discrete time series, with optional interpolation.
     * @param timeBucketAverages A map of discrete time buckets to their average/sum values.
     * @param doInterpolateBetweenBuckets If true, performs linear interpolation between known data points.
     * @return A [DoubleArray] of size [Predictor.TIME_SERIES_SAMPLE_COUNT] representing the final, discrete time series.
     */
    private fun bucketsToDiscreteTimeSeries(
        timeBucketAverages: SortedMap<Int, Double>,
        doInterpolateBetweenBuckets: Boolean,
    ): DoubleArray {
        //resulting time series, with interpolated, discrete values
        val discreteTimeSeries = DoubleArray(Predictor.TIME_SERIES_SAMPLE_COUNT) { 0.0 }

        //fill known points
        for ((timeStep, value) in timeBucketAverages) {
            val index = timeStep
            if (index in discreteTimeSeries.indices) {
                discreteTimeSeries[index] = value
            }
        }

        //we don't want interpolation on time series like distance/steps that are aggregated
        if (!doInterpolateBetweenBuckets) return discreteTimeSeries

        //add interpolation steps between those points
        for ((startPoint, endPoint) in timeBucketAverages.entries.zipWithNext()) {
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
     * Fills in the edge buckets (start and end) of a time series if they are missing.
     * This is done by taking the first known value and assigning it to the start bucket (index 0),
     * and taking the last known value and assigning it to the end bucket. This results in constant extrapolation at the edges.
     * @param timeBucketAverages The sorted map of time buckets to their values.
     * @return The same map, but potentially with the start and end buckets filled in.
     */
    private fun extrapolateEdgeBuckets(
        timeBucketAverages: SortedMap<Int, Double>,
    ): SortedMap<Int, Double> {
        require(timeBucketAverages.isNotEmpty())

        val firstValue = timeBucketAverages.getValue(timeBucketAverages.firstKey())
        val lastValue = timeBucketAverages.getValue(timeBucketAverages.lastKey())
        val lastKey = (Predictor.TIME_SERIES_SAMPLE_COUNT - 1)

        timeBucketAverages.putIfAbsent(0, firstValue)
        timeBucketAverages.putIfAbsent(lastKey, lastValue);

        return timeBucketAverages
    }

    /**
     * Discretizes a list of timed data points into a fixed-size time series array.
     * Depending on the [IPreprocessor.GenericTimeSeriesEntries.type], it either calculates averages and interpolates
     * for continuous data (like heart rate), or sums for aggregated data (like steps) in each time bucket.
     * For continuous data, it also extrapolates to the start and end of the time series if needed.
     * @param input The generic time series data to be processed.
     * @return A [DoubleArray] representing the discretized time series.
     */
    fun discretizeTimeSeries(
        input: IPreprocessor.GenericTimeSeriesEntries
    ): DoubleArray {
        var timeBucketAverages = calculateTimeBucketAverages(input)

        //optionally pin the first and last known value to the borders so that we don't get missing
        //values,this will lead to constant extrapolation at the edges
        //only makes sense with continuous time series(bpm), not with aggregated ones (like steps)
        val isContinuous =
            (input.type == IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType.CONTINUOUS)

        if (isContinuous) {
            timeBucketAverages = extrapolateEdgeBuckets(timeBucketAverages)
        }

        val discreteTimeSeries =
            bucketsToDiscreteTimeSeries(
                timeBucketAverages,
                doInterpolateBetweenBuckets = isContinuous
            )

        return discreteTimeSeries
    }
}