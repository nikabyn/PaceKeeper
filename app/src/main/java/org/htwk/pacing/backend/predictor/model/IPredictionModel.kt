package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer
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

    /*fun backTestMany(
        inputMTSD: MultiTimeSeriesDiscrete,
        targetTimeSeries: TimeSeriesDiscretizer.SingleDiscreteTimeSeries,
        predictionHorizon: PredictionHorizon
    ): DoubleArray*/

    //TODO: we shouldn't differentiate between derivative or proportional energy level at this point, because the derivative can be passed from the callee, the model implementation itself shouldn't care wether it's predicting derivative or proportional
    fun predict(input: MultiTimeSeriesDiscrete, predictionHorizon: PredictionHorizon): Double
    fun train(input: MultiTimeSeriesDiscrete, target: DoubleArray)

    //predict one timepoint
    /*fun predictSingle(
        input: MultiTimeSeriesDiscrete,
        predictionHorizon: PredictionHorizon,
        lastPredictedEnergy: Double
    ): Double*/
}