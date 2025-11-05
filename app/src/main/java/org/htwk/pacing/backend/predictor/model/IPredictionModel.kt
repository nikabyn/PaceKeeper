package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.MultiTimeSeriesDiscrete

interface IPredictionModel {
    //predict one future timepoint
    fun predict(input: MultiTimeSeriesDiscrete): Double
}