package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries

object Preprocessor {
    /**
     * Executes the preprocessing pipeline on raw time series data.
     *
     * This function first cleans the input data, ensures fallback filling where it is empty then
     * turns it into a discrete, uniformly sampled format (multi time series per metric) suitable
     * for the prediction model.
     *
     * @param raw The raw time series data, containing lists of data points for different metrics.
     * @param fixedParameters Additional fixed parameters that might influence the preprocessing. (Currently unused).
     * @return A [MultiTimeSeriesDiscrete] object containing the processed and discretized time series data.
     */
    fun run(
        raw: MultiTimeSeriesEntries,
        fixedParameters: FixedParameters
    ): MultiTimeSeriesDiscrete {
        //val (rawCleaned, qualityRatios) = cleanInputData(raw)
        //val ensuredDataUsingFallback = ensureDataFallback(rawCleaned)

        return MultiTimeSeriesDiscrete.fromEntries(/*ensuredDataUsingFallback*/ raw);
    }
}