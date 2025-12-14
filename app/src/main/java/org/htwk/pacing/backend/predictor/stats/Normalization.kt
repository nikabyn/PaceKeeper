package org.htwk.pacing.backend.predictor.stats

import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.set
import org.jetbrains.kotlinx.multik.ndarray.operations.average
import org.jetbrains.kotlinx.multik.ndarray.operations.forEach
import org.jetbrains.kotlinx.multik.ndarray.operations.forEachIndexed
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
 * Normalizes the input data, returns the normalized form and the stochastic properties for
 * later denormalization
 * @param input The input data to be normalized.
 * @see StochasticDistribution
 * @see denormalize
 */

fun normalize(
    input: D1Array<Double>,
    stochasticDistribution: StochasticDistribution = StochasticDistribution.ofArray(input)
): StochasticDistribution {
    if (stochasticDistribution.stddev == 0.0) {
        input.forEachIndexed { idx, _ -> input[idx] = 0.0 }
        return stochasticDistribution
    }

    for (i in 0 until input.size) {
        input[i] = (input[i] - stochasticDistribution.mean) / stochasticDistribution.stddev
    }
    return stochasticDistribution
}

/**
 * Denormalizes the [input] array
 * @param input The array to be denormalized.
 * @see StochasticDistribution
 * @see normalize
 */
fun denormalize(
    input: D1Array<Double>,
    stochasticDistribution: StochasticDistribution
) {
    for (i in 0 until input.size) {
        input[i] = input[i] * stochasticDistribution.stddev + stochasticDistribution.mean
    }
}