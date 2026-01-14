package org.htwk.pacing.predictor.stats

import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import kotlin.collections.get
import kotlin.math.pow

data class StochasticDistribution(
    val mean: Double,
    val stddev: Double
) {
    companion object {
        fun ofArray(input: D1Array<Double>): StochasticDistribution {
            val mean = input.average()

            var stddev = 0.0
            input.forEach { x -> stddev += (x - mean).pow(2.0) }
            stddev /= input.size
            stddev = stddev.pow(0.5)

            return StochasticDistribution(mean, stddev)
        }
    }
}

/**
 * Normalizes the array, and returns its [StochasticDistribution] for later denormalization
 * @param StochasticDistribution The stochastic distribution for rescaling the array (only provide
 *                               if you need a custom adjusted distribution)
 * @see StochasticDistribution
 * @see denormalize
 */

fun D1Array<Double>.normalize(
    stochasticDistribution: StochasticDistribution = StochasticDistribution.ofArray(this)
): StochasticDistribution {
    if (stochasticDistribution.stddev == 0.0) {
        this.forEachIndexed { idx, _ -> this[idx] = 0.0 }
        return stochasticDistribution
    }

    for (i in 0 until this.size) {
        this[i] = (this[i] - stochasticDistribution.mean) / stochasticDistribution.stddev
    }
    return stochasticDistribution
}

/**
 * Normalizes a single value using standard score (z-score) normalization.
 *
 * @param value The value to normalize.
 * @param stochasticDistribution The distribution (mean, stddev) to use for normalization.
 *                               If omitted, the value is normalized against itself, resulting in 0.0.
 * @return The normalized value. Returns 0.0 if the standard deviation is zero.
 */
fun normalizeSingleValue(
    value: Double,
    stochasticDistribution: StochasticDistribution = StochasticDistribution.ofArray(mk.ndarray(doubleArrayOf(value)))
): Double {
    return if (stochasticDistribution.stddev == 0.0) {
        0.0
    } else {
        (value - stochasticDistribution.mean) / stochasticDistribution.stddev
    }
}


/**
 * Denormalizes the array
 * @param StochasticDistribution The stochastic distribution for rescaling the array.
 * @see StochasticDistribution
 * @see normalizeSingleValue
 */
fun D1Array<Double>.denormalize(
    stochasticDistribution: StochasticDistribution
) {
    for (i in 0 until this.size) {
        this[i] = this[i] * stochasticDistribution.stddev + stochasticDistribution.mean
    }
}