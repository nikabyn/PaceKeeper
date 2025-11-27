package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.htwk.pacing.backend.predictor.preprocessing.FallbackHandler.ensureDataFallback

object Preprocessor : IPreprocessor {
    /**
     * Executes the preprocessing pipeline on raw time series data.
     *
     * This function first cleans the input data and then transforms the raw, continuous time series
     * data for various metrics (like heart rate and distance) into a discrete, uniformly sampled
     * format suitable for the prediction model. It converts each metric into a common
     * [IPreprocessor.SingleGenericTimeSeriesEntries] format before passing it to the [TimeSeriesDiscretizer]
     * for processing.
     *
     * @param raw The raw time series data, containing lists of data points for different metrics.
     * @param fixedParameters Additional fixed parameters that might influence the preprocessing. (Currently unused).
     * @return A [MultiTimeSeriesDiscrete] object containing the processed and discretized time series data.
     */
    override fun run(
        raw: MultiTimeSeriesEntries,
        fixedParameters: FixedParameters
    ): MultiTimeSeriesDiscrete {
        val (rawCleaned, qualityRatios) = cleanInputData(raw)
        val ensuredDataUsingFallback = ensureDataFallback(rawCleaned)

        return MultiTimeSeriesDiscrete.fromEntries(ensuredDataUsingFallback);
    }
}