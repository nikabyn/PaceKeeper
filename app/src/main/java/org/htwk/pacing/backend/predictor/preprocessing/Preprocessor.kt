package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.MultiTimeSeriesDiscrete

object Preprocessor : IPreprocessor {
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
        val (rawCleaned, qualityRatios) = cleanInputData(raw)

        return MultiTimeSeriesDiscrete(
            timeStart = rawCleaned.timeStart,
            heartRate = DiscretePID.from(
                TimeSeriesDiscretizer.discretizeTimeSeries(
                    IPreprocessor.GenericTimeSeriesEntries(
                        timeStart = rawCleaned.timeStart,
                        data = rawCleaned.heartRate.map(::GenericTimedDataPoint),
                        type = IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType.CONTINUOUS
                    )
                )
            ),
            distance = DiscreteIntegral.from(
                TimeSeriesDiscretizer.discretizeTimeSeries(
                    IPreprocessor.GenericTimeSeriesEntries(
                        timeStart = rawCleaned.timeStart,
                        data = raw.distance.map(::GenericTimedDataPoint),
                        type = IPreprocessor.GenericTimeSeriesEntries.TimeSeriesType.CONTINUOUS
                    )
                )
            )
        )
    }
}