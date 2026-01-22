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
     * Depending on the [TimeSeriesSignalClass], it either calculates the average
     * (for continuous data) or the sum (for aggregated data) for each bucket.
     * @param input The generic time series data to be processed.
     * @return A map where keys are discrete time buckets (relative to startTime) and values are the averaged data points.
     */
    private fun calculateTimeBucketAverages(
        input: GenericTimedDataPointTimeSeries,
    ): SortedMap<Int, Double> {
        val timeStart = input.timeStart
        val entries = input.data

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
            if (input.isContinuous) {
                // TODO: weighted resampling/average, as HR data points are probably unevenly spaced
                group.map { it.value }.average() //for continuous values, calculate average
            } else {
                group.sumOf { it.value } //for aggregated values, like steps, just sum, don't average
            }
        }

        return timeBucketValues.toSortedMap()
    }

    /**
     * Populates a `DoubleArray` with values from a map of time bucket averages.
     * Time steps for which no values exist are left empty.
     *
     * @param timeBucketAverages A map of time step indices to their values.
     * @param discreteTimeSeries The array to populate.
     */
    private fun discretizeBuckets(
        timeBucketAverages: SortedMap<Int, Double>,
        discreteTimeSeries: DoubleArray
    ) {
        for ((timeStep, value) in timeBucketAverages) {
            val index = timeStep
            if (index in discreteTimeSeries.indices) {
                discreteTimeSeries[index] = value
            }
        }
    }

    /**
     * Fills gaps in a discrete time series array using linear interpolation.
     *
     * This method mutates the `discreteTimeSeries` array by calculating and filling in
     * the values for empty steps between the known data points provided in `timeBucketAverages`.
     *
     * @param timeBucketAverages A sorted map of known time step indices to their values.
     * @param discreteTimeSeries The array to be populated with interpolated values.
     */
    private fun discreteInterpolateBetweenBuckets(
        timeBucketAverages: SortedMap<Int, Double>,
        discreteTimeSeries: DoubleArray
    ) {
        for ((startPoint, endPoint) in timeBucketAverages.entries.zipWithNext()) {
            val (x0, y0) = startPoint
            val (x1, y1) = endPoint

            val intervalSteps = (x1 - x0)
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
        targetLength: Int
    ): DoubleArray {
        //resulting time series, with interpolated, discrete values
        val discreteTimeSeries = DoubleArray(targetLength) { 0.0 }

        //fill known points from map
        discretizeBuckets(timeBucketAverages, discreteTimeSeries)

        //we don't want interpolation on time series like distance/steps that are aggregated
        if (!doInterpolateBetweenBuckets) return discreteTimeSeries

        //add interpolation steps between those points
        discreteInterpolateBetweenBuckets(timeBucketAverages, discreteTimeSeries)

        return discreteTimeSeries

    }

    /**
     * Fills the edge buckets (index 0 and the final index) of a time series map if they are missing.
     * This method **mutates** the input map. It's designed as a private transformation step.
     *
     * This is done by taking the first known value and assigning it to the start bucket (index 0),
     * and taking the last known value and assigning it to the last bucket. This results in constant
     * extrapolation at the edges.
     *
     * @param timeBucketAverages The sorted map of time buckets to their values, which will be modified in-place.
     */
    private fun fillEdgeBuckets(
        timeBucketAverages: SortedMap<Int, Double>,
        targetLength: Int
    ) {
        if (timeBucketAverages.isEmpty()) return

        val firstValue = timeBucketAverages.getValue(timeBucketAverages.firstKey())
        val lastValue = timeBucketAverages.getValue(timeBucketAverages.lastKey())
        val lastKey = (targetLength - 1)

        timeBucketAverages.putIfAbsent(0, firstValue)
        timeBucketAverages.putIfAbsent(lastKey, lastValue)
    }

    /**
     * Discretizes a list of timed data points into a fixed-size time series array.
     * Depending on the [IPreprocessor.GenericTimeSeriesEntries.signalClass], it either calculates averages and interpolates
     * for continuous data (like heart rate), or sums for aggregated data (like steps) in each time bucket.
     * For continuous data, it also extrapolates to the start and end of the time series if needed.
     * @param input The generic time series data to be processed.
     * @param isContinuous Determines whether to perform interpolation or not (only interpolate/edge
     *                     fill for continuous metric, not for aggregated metrics like steps)
     * @return A [DoubleArray] representing the discretized time series.
     */
    fun discretizeTimeSeries(
        input: GenericTimedDataPointTimeSeries,
        targetLength: Int = (input.duration / Predictor.TIME_SERIES_STEP_DURATION).toInt()
    ): Pair<DoubleArray, SortedMap<Int, Double>> {
        val timeBucketAverages = calculateTimeBucketAverages(input)

        //optionally pin the first and last known value to the borders so that we don't get missing
        //values,this will lead to constant extrapolation at the edges
        //only makes sense with continuous time series(bpm), not with aggregated ones (like steps)
        if (input.isContinuous) {
            fillEdgeBuckets(timeBucketAverages, targetLength)
        }

        val discreteTimeSeries =
            bucketsToDiscreteTimeSeries(
                timeBucketAverages,
                doInterpolateBetweenBuckets = input.isContinuous,
                targetLength = targetLength
            )

        return Pair(discreteTimeSeries, timeBucketAverages)
    }
}