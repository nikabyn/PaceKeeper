package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.model.LinearCombinationPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete

interface IPredictionModel {
    //predict one future timepoint
    fun predict(input: MultiTimeSeriesDiscrete, predictionHorizon: PredictionHorizon): Double
}