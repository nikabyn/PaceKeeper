package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

interface IPredictionModel {
    enum class PredictionHorizon(val howFar: Duration) {
        NOW(0.hours),
        FUTURE(2.hours),
    }

    //extension function that turns prediction horizon into discrete steps count
    val PredictionHorizon.howFarInSamples: Int
        get() = (howFar / Predictor.TIME_SERIES_STEP_DURATION).toInt()


    //predict one timepoint
    fun predict(
        input: MultiTimeSeriesDiscrete,
        predictionHorizon: PredictionHorizon
    ): Double
}