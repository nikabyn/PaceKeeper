package org.htwk.pacing.predictor.preprocessing

import org.htwk.pacing.predictor.Predictor

object Preprocessor {
    /**
     * Executes the preprocessing pipeline on raw time series data.
     *
     * This function first cleans the input data, ensures fallback filling where it is empty then
     * turns it into a discrete, uniformly sampled format (multi time series per metric) suitable
     * for the prediction model.
     *
     * @param input The raw time series data, containing lists of data points for different metrics.
     * @param fixedParameters Additional fixed parameters that might influence the preprocessing. (Currently unused).
     * @return A [MultiTimeSeriesDiscrete] object containing the processed and discretized time series data.
     */
    fun run(
        input: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): MultiTimeSeriesDiscrete {
        return MultiTimeSeriesDiscrete.fromEntries(input);
    }
}
