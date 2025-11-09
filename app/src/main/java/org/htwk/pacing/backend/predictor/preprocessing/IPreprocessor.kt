package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral

interface IPreprocessor {

    sealed interface DiscreteTimeSeriesResult {

        //for results of continuous time series (like heart rate)
        data class DiscretePID(
            val proportional: DoubleArray, // "P" part of PID
            val integral: DoubleArray, // "I" part PID
            val derivative: DoubleArray // "D" part of PID
        ) : DiscreteTimeSeriesResult {
            companion object {
                fun from(proportionalInput: DoubleArray) = DiscretePID(
                    proportionalInput,
                    proportionalInput.discreteTrapezoidalIntegral(),
                    proportionalInput.discreteDerivative()
                )
            }
        }

        //for results of integrable/summable time series (aggregations like steps)
        data class DiscreteIntegral(
            val integral: DoubleArray
        ) : DiscreteTimeSeriesResult {
            companion object {
                fun from(proportionalInput: DoubleArray) = DiscreteIntegral(
                    proportionalInput.discreteTrapezoidalIntegral()
                )
            }
        }
    }

    data class GenericTimeSeriesEntries(
        val timeStart: Instant,
        val data: List<GenericTimedDataPoint>,
        val type: TimeSeriesType,
    ) {
        enum class TimeSeriesType {
            CONTINUOUS,
            AGGREGATED,
        }
    }


    //only used internally between preprocessor and model
    //see ui#38, comment: https://gitlab.dit.htwk-leipzig.de/pacing-app/ui/-/issues/38#note_248963
    data class MultiTimeSeriesDiscrete(
        val timeStart: Instant,

        //will be expanded with more vitals
        //class 1 (continuous values)
        val heartRate: DiscretePID,
        //class 2 (aggregated values)
        val distance: DiscreteIntegral
    )

    data class QualityRatios(
        val cleanedHeartRatesRatio: Percentage,
        val cleanedDistancesRatio: Percentage
    )

    fun run(
        raw: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): MultiTimeSeriesDiscrete
}