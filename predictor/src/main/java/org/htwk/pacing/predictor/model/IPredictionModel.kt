package org.htwk.pacing.predictor.model

import org.htwk.pacing.predictor.Predictor
import org.htwk.pacing.predictor.preprocessing.MultiTimeSeriesDiscrete
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

interface IPredictionModel {
    enum class PredictionHorizon(val howFar: Duration) {
        NOW(10.minutes),
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