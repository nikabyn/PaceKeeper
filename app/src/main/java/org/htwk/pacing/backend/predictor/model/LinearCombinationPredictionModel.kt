package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.htwk.pacing.ui.math.sigmoidStable


object LinearCombinationPredictionModel : IPredictionModel {
    val scaleParam = 1.0f; //TODO: replace with non-dummy, dynamic weight/offset parameters

    /**
     * Predicts the next energy level data point based on the preprocessed time series data.
     *
     * This function currently uses a simplified model, applying a sigmoid function to the last
     * proportional heart rate value. The final model is intended to use a linear combination

     * of various time series features (e.g., integrals, derivatives) once they are implemented.
     *
     * @param input The [IPreprocessor.MultiTimeSeriesDiscrete] object containing the preprocessed
     *              time series data, such as heart rate.
     * @return A [Double] representing the extrapolated energy level, scaled between 0.0 and 1.0.
     */
    override fun predict(input: IPreprocessor.MultiTimeSeriesDiscrete): Double {
        //TODO: use integral and other derived time series in a linear combination model once they're
        // implemented in the preprocessor
        return sigmoidStable(scaleParam * input.heartRate.proportional.last());
    }
}