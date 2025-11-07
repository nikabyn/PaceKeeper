package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.MultiTimeSeriesDiscrete
import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral

object Preprocessor : IPreprocessor {
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
        val p = TimeSeriesDiscretizer.discretizeTimeSeries(
            timeStart = timeStart,
            input = input,
            isAggregation = false
        )

        return DiscretePID(p, p.discreteTrapezoidalIntegral(), p.discreteDerivative())
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
        val p = TimeSeriesDiscretizer.discretizeTimeSeries(
            timeStart = timeStart,
            input = input,
            isAggregation = true
        )
        return DiscreteIntegral(p.discreteTrapezoidalIntegral())
    }

    //class 3) (unused for now), see ui#38
    private fun processDailyConstant(): Double {
        return 0.0
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
            heartRate = processContinuous(
                raw.timeStart,
                raw.heartRate.map(::GenericTimedDataPoint)
            ),
            distance = processAggregated(raw.timeStart, raw.distance.map(::GenericTimedDataPoint))
        )
    }
}