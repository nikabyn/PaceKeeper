package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

interface IPredictionModel {
    enum class PredictionHorizon(val howFar: Duration) {
        NOW(0.minutes),
        FUTURE(6.hours),
    }

    //extension function that turns prediction horizon into discrete steps count
    val PredictionHorizon.howFarInSamples: Int
        get() = (howFar / Predictor.TIME_SERIES_STEP_DURATION).toInt()

    fun predict(input: MultiTimeSeriesDiscrete, offset: Int, horizon: PredictionHorizon): Double
    fun train(input: MultiTimeSeriesDiscrete, target: DoubleArray)
}