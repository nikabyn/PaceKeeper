package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object Preprocessor {
    private data class GenericTimedDataPoint(
        val time: kotlinx.datetime.Instant,
        val value: Double,
    )

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

    //class 1) continuous series like Heart BPM
    private fun processContinuous(
        input: List<GenericTimedDataPoint>,
        now10min: Instant
    ): DiscretePID {
        val p = discretizeTimeSeries(input, now10min)
        //TODO: implement functions for discrete integral, derivative, use them here
        return DiscretePID(p, doubleArrayOf(), doubleArrayOf())
    }

    //class 2) aggregated/counted series like steps
    private fun processAggregated(
        input: List<GenericTimedDataPoint>,
        now10min: Instant
    ): DiscreteIntegral {
        val p = discretizeTimeSeries(input, now10min)
        //TODO: implement function for discrete derivative, use it here
        return DiscreteIntegral(doubleArrayOf())
    }

    //class 3)
    private fun processDailyConstant(): Double {
        return 0.0;
    }

    private fun discretizeTimeSeries(
        input: List<GenericTimedDataPoint>,
        now10min: Instant,
        step: Duration = 10.minutes,
        holdEdges: Boolean = true // bei false: lin. Extrapolation
    ): DoubleArray {
        return doubleArrayOf()
    }

    fun run(
        raw: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): Predictor.MultiTimeSeriesDiscrete {
        //TODO: see other ticket
        return Predictor.MultiTimeSeriesDiscrete(
            timeStart = Clock.System.now() - Predictor.TIME_SERIES_DURATION,
            heartRate = processContinuous(raw.heartRate.map { it ->
                GenericTimedDataPoint(
                    it.time,
                    it.bpm.toDouble()
                )
            }, raw.timeStart)
        )
    }
}