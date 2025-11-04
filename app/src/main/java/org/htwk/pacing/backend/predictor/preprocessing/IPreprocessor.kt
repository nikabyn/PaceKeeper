package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID

interface IPreprocessor {

    sealed interface DiscreteTimeSeriesResult {

        //for results of continuous time series (like heart rate)
        class DiscretePID(
            val proportional: DoubleArray, // "P" part of PID
            val integral: DoubleArray, // "I" part PID
            val derivative: DoubleArray // "D" part of PID
        ) : DiscreteTimeSeriesResult

        //for results of integrable/summable time series (aggregations like steps)
        data class DiscreteIntegral(
            val integral: DoubleArray,
        ) : DiscreteTimeSeriesResult
    }

    //only used internally between preprocessor and model
    //see ui#38, comment: https://gitlab.dit.htwk-leipzig.de/pacing-app/ui/-/issues/38#note_248963
    data class MultiTimeSeriesDiscrete(
        val timeStart: Instant,

        //will be expanded with more vitals
        //class 1 (continuous values)
        val heartRate: DiscretePID,
        //class 2 (aggregated values)
    )

    fun run(
        raw: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): MultiTimeSeriesDiscrete
}