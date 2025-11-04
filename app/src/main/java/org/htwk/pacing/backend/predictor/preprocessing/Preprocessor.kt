package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.Companion.TIME_SERIES_DURATION
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscretePID
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.MultiTimeSeriesDiscrete
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

object Preprocessor : IPreprocessor {
    private data class GenericTimedDataPoint(
        val time: Instant,
        val value: Double,
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
        //constant extrapolation of first value in time series
        require(input.isNotEmpty());

        //TODO: replace with actual resampling code (this is just a placeholder for a constant fill)
        return DoubleArray((TIME_SERIES_DURATION.inWholeHours * 6).toInt()) { input[0].value };
    }

    override fun run(
        raw: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): MultiTimeSeriesDiscrete {
        //TODO: see other ticket
        return MultiTimeSeriesDiscrete(
            timeStart = raw.timeStart,
            heartRate = processContinuous(raw.heartRate.map { it ->
                GenericTimedDataPoint(
                    it.time,
                    it.bpm.toDouble()
                )
            }, raw.timeStart)
        )
    }
}