package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor

interface IPreprocessor {
    //for results of continuous time series (like heart rate)
    data class DiscretePID(
        val proportional: DoubleArray,
        val integral: DoubleArray,
        val derivative: DoubleArray
    )

    //for results of integrable/summable time series (aggregations like steps)
    data class DiscreteIntegral(
        val integral: DoubleArray,
    )

    //only used internally between preprocessor and model
    data class MultiTimeSeriesDiscrete(
        //TODO: expand with more vitals
        val timeStart: kotlinx.datetime.Instant,
        //class 1 (continuous values)
        val heartRate: DiscretePID,
        //class 2 (aggregated values)
    )

    fun run(
        raw: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): MultiTimeSeriesDiscrete
}