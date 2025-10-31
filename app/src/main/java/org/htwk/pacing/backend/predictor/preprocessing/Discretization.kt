package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.mlmodel.MLModel
import org.htwk.pacing.ui.math.roundInstantToResolution
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

//SEE: ui#38 comment for explanation on this

//class 1) continuous series like Heart BPM

//for continuous time series results
data class PIDResult(
    val proportional: FloatArray,
    val integral: FloatArray,
    val derivative: FloatArray
)

//create a generic template function that can take list of type T and return list of type R
inline fun <T, R> process(input: List<T>, processor: (T) -> R): List<R> {
    return input.map(processor)
}

data class GenericTimedDataPoint(
    val time: kotlinx.datetime.Instant,
    val value: Float,
)

private fun discreteToPID(p: FloatArray, step: Duration = 10.minutes): PIDResult {
    val modelInputSize = MLModel::INPUT_SIZE.get().toInt()
    val modelInputDuration = MLModel::INPUT_DAYS.get().toInt().days
    val stepSec = step.inWholeSeconds.toFloat()
    //numeric integral (Trapezoid rule, cumulative)
    val i = FloatArray(modelInputSize)
    for (k in 1 until modelInputSize) {
        i[k] = i[k - 1] + 0.5f * (p[k - 1] + p[k]) * stepSec
    }

    //numeric derivative (central, boundaries forward/backward)
    val d = FloatArray(modelInputSize)
    if (modelInputSize > 1) {
        d[0] = (p[1] - p[0]) / stepSec
        for (k in 1 until modelInputSize - 1) {
            d[k] = (p[k + 1] - p[k - 1]) / (2f * stepSec)
        }
        d[modelInputSize - 1] = (p[modelInputSize - 1] - p[modelInputSize - 2]) / stepSec
    }

    return PIDResult(p, i, d);
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

fun discretizeTimeSeries(
    heartRateData: List<GenericTimedDataPoint>,
    now10min: kotlinx.datetime.Instant,
    step: Duration = 10.minutes
): FloatArray {
    val modelInputSize = MLModel::INPUT_SIZE.get().toInt()
    val modelInputDuration = MLModel::INPUT_DAYS.get().toInt().days
    val modelInputBegin = now10min - modelInputDuration
    val stepSec = step.inWholeSeconds.toFloat()

    //fill step buckets with average per step interval
    val avgPerBucket: Map<Instant, Float> =
        heartRateData
            .groupBy { roundInstantToResolution(it.time, step) }
            .mapValues { (_, group) -> group.map { it.value }.average().toFloat() }

    //no data -> return empty array
    if (avgPerBucket.isEmpty()) {
        val empty = FloatArray(modelInputSize)
        val has = BooleanArray(modelInputSize)
        return floatArrayOf();
    }

    val p = FloatArray(modelInputSize) { Float.NaN }
    val hasData = BooleanArray(modelInputSize)

    // write values to
    for (i in 0 until modelInputSize) {
        val t = modelInputBegin + (i * 10).minutes
        avgPerBucket[t]?.let { v ->
            p[i] = v
            hasData[i] = true
        }
    }

    // filter missing values
    val known = (0 until modelInputSize).filter { !p[it].isNaN() }
    if (known.isEmpty()) {
        val empty = FloatArray(modelInputSize)
        return floatArrayOf()
    }

    // extrapolate to left and right border (from outermost data points)
    for (i in 0 until known.first()) p[i] = p[known.first()]
    for (i in known.last() + 1 until modelInputSize) p[i] = p[known.last()]

    // discrete linear interpolation
    var prev = known.first()
    for (next in known.drop(1)) {
        val v0 = p[prev];
        val v1 = p[next]
        val span = (next - prev).toFloat()
        for (i in prev + 1 until next) {
            p[i] = v0 + (v1 - v0) * ((i - prev) / span)
        }
        prev = next
    }

    return p;
}


//class 2) aggregated/counted series like steps
fun processAggregated(): FloatArray {
    return floatArrayOf();
}

//class 3)
fun processDailyConstant(): Float {
    return 0.0f;
}