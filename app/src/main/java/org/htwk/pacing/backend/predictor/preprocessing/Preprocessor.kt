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

    //for continuous time series results
    data class DiscretePID(
        val proportional: DoubleArray,
        val integral: DoubleArray,
        val derivative: DoubleArray
    )

    data class DiscreteIntegral(
        val integral: DoubleArray,
    )

    //class 1) continuous series like Heart BPM
    private fun processContinuous(
        input: List<GenericTimedDataPoint>,
        now10min: Instant
    ): DiscretePID {
        val p = discretizeTimeSeries(input, now10min)
        return DiscretePID(p, computeDiscreteIntegral(p), computeDiscreteDerivative(p))
    }

    //class 2) aggregated/counted series like steps
    private fun processAggregated(
        input: List<GenericTimedDataPoint>,
        now10min: Instant
    ): DiscreteIntegral {
        val p = discretizeTimeSeries(input, now10min)
        return DiscreteIntegral(computeDiscreteIntegral(p))
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
        val size = Predictor.timeSeriesSampleCount
        val duration = Predictor.timeSeriesDuration
        val start = now10min - duration

        val stepMs = step.inWholeMilliseconds
        val anchorMs = start.toEpochMilliseconds()

        fun bucketMs(t: Instant): Long {
            val ms = t.toEpochMilliseconds()
            val k = Math.floorDiv(ms - anchorMs, stepMs)
            return anchorMs + k * stepMs
        }

        //calculate average per step-size bucket (not adjusted, perhaps replace by time-weighted avg.)
        val avgByBucket: Map<Long, Double> =
            input.groupBy { bucketMs(it.time) }
                .mapValues { (_, xs) -> xs.map { it.value }.average() }

        //fixed rasterization times
        val p = DoubleArray(size) { Double.NaN }
        for (i in 0 until size) {
            val tMs = anchorMs + i.toLong() * stepMs
            avgByBucket[tMs]?.let { p[i] = it }
        }

        val known = (0 until size).filter { !p[it].isNaN() }
        if (known.isEmpty()) return DoubleArray(size) //return zero-array if no datapoints

        //linearly interpolate between datapoints
        var a = known.first()
        for (b in known.drop(1)) {
            val v0 = p[a];
            val v1 = p[b];
            val span = (b - a)
            for (i in a + 1 until b) p[i] = v0 + (v1 - v0) * ((i - a) / span)
            a = b
        }

        // boundaries: constant extrapolation
        for (i in 0 until known.first()) p[i] = p[known.first()] //extrapolate towards left
        for (i in known.last() + 1 until size) p[i] = p[known.last()] //extrapolate towards right

        return p
    }

    fun run(
        raw: Predictor.MultiTimeSeriesEntries,
        fixedParameters: Predictor.FixedParameters
    ): Predictor.MultiTimeSeriesDiscrete {
        //TODO: see other ticket
        return Predictor.MultiTimeSeriesDiscrete(
            timeStart = Clock.System.now() - Predictor.timeSeriesDuration,
            heartRate = processContinuous(raw.heartRate.map { it ->
                GenericTimedDataPoint(
                    it.time,
                    it.bpm.toDouble()
                )
            }, raw.timeStart)
        )
    }
}