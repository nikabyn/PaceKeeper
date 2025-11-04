package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.Companion.TIME_SERIES_DURATION
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.MultiTimeSeriesDiscrete
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object Preprocessor : IPreprocessor {

    /**
     * A generic data structure to unify different time series data types
     * before passing them to the common processing functions.
     */
    private data class GenericTimedDataPoint(
        val time: Instant,
        val value: Double,
    )

    /**
     * Processes continuous time series data, like heart rate.
     * @param input The list of timed data points.
     * @param now10min The reference start time for discretization.
     * @return A [DiscretePID] object containing the discretized series and its derivatives/integrals.
     */
    private fun processContinuous(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
    ): DiscretePID {
        val p = discretizeTimeSeries(timeStart + TIME_SERIES_DURATION, input)
        //TODO: implement functions for discrete integral, derivative, use them here
        return DiscretePID(p, doubleArrayOf(), doubleArrayOf())
    }

    /**
     * Processes aggregated/counted time series data, like step count.
     * @param input The list of timed data points.
     * @param now10min The reference start time for discretization.
     * @return A [DiscreteIntegral] object containing the discretized series derivative.
     */
    private fun processAggregated(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
    ): DiscreteIntegral {
        val p = discretizeTimeSeries(timeStart + TIME_SERIES_DURATION, input)
        //TODO: implement function for discrete derivative, use it here
        return DiscreteIntegral(doubleArrayOf())
    }

    //class 3) (unused for now), see ui#38
    private fun processDailyConstant(): Double {
        return 0.0;
    }

    /**
     * Resamples a time series to a uniform time grid.
     *
     * This function converts arbitrarily timed data points into a uniformly spaced series
     * starting at `timeStart`, with a fixed `step` duration.
     *
     * @param timeStart The start time for the resampling grid.
     * @param input The list of `GenericTimedDataPoint`s to resample. Must not be empty.
     * @param step The time interval for the output grid.
     * @param holdEdges If `true`, uses constant extrapolation for values outside the input time range.
     * @return A `DoubleArray` with the resampled time series values.
     * @throws IllegalArgumentException if `input` is empty.
     */
    private fun discretizeTimeSeries(
        timeStart: Instant,
        input: List<GenericTimedDataPoint>,
        step: Duration = 10.minutes,
        holdEdges: Boolean = true // bei false: lin. Extrapolation
    ): DoubleArray {
        //constant extrapolation of first value in time series
        require(input.isNotEmpty());

        //TODO: replace with actual resampling code (this is just a placeholder for a constant fill)
        return DoubleArray((TIME_SERIES_DURATION.inWholeHours * 6).toInt()) { input[0].value };
    }

    /**
     * Executes the preprocessing pipeline on raw time series data.
     *
     * This function takes raw, continuous time series data for various metrics (like heart rate)
     * and transforms it into a discrete, uniformly sampled format suitable for the prediction model.
     * It handles the conversion of each metric into a common `GenericTimedDataPoint` format
     * before passing it to specialized processing functions (e.g., `processContinuous`).
     *
     * @param raw The raw time series data, containing lists of data points for different metrics.
     * @param fixedParameters Additional fixed parameters that might influence the preprocessing, though currently unused.
     * @return A [MultiTimeSeriesDiscrete] object containing the processed, discretized time series data.
     */
    override fun run(
        raw: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): MultiTimeSeriesDiscrete {

        return MultiTimeSeriesDiscrete(
            timeStart = raw.timeStart,
            heartRate = processContinuous(raw.timeStart, raw.heartRate.map { it ->
                GenericTimedDataPoint(
                    it.time,
                    it.bpm.toDouble()
                )
            })
        )
    }
}