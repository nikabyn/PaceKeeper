package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesSamples


//actual mathematical model that we use for making a prediction
object Model {
    private val paramA = 0.0
    private val paramB = 0.0

    //returns extrapolated energy level
    fun predict(input: MultiTimeSeriesSamples): FloatArray {
        return floatArrayOf()
    }
}