package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.htwk.pacing.ui.math.sigmoidStable


object LinearCombinationPredictionModel : IPredictionModel {
    val scaleParam = 1.0f; //TODO: replace with non-dummy, dynamic weight/offset parameters

    //returns extrapolated energy level point
    override fun predict(input: IPreprocessor.MultiTimeSeriesDiscrete): Double {
        //TODO: use integral and other derived time series in a linear combination model once they're
        // implemented in the preprocessor
        return sigmoidStable(scaleParam * input.heartRate.proportional.last());
    }
}