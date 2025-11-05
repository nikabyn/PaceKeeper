package org.htwk.pacing.ui.math

import kotlin.math.PI

//SEE: ui#38 comment for explanation on this file

/**
 * Discrete calculus on uniformly sampled DoubleArray.
 * Assumes constant spacing dt > 0 between samples.
 *
 **/

fun DoubleArray.discreteDerivative(): DoubleArray {
    require(size >= 2) { "Need at least 2 samples." }

    val n = size
    val d = DoubleArray(n)

    if (n == 2) {
        val s = (this[1] - this[0])
        d[0] = s; d[1] = s
        return d
    }

    // numerically 2nd-order one-sided at boundaries, required for reducing term with numerically 2nd-order derivative
    d[0] = (-3.0 * this[0] + 4.0 * this[1] - this[2])
    d[n - 1] = (3.0 * this[n - 1] - 4.0 * this[n - 2] + this[n - 3])

    // numerically 2nd-order centered interior (for noise/error reduction)
    // this means the cumulative error is proportional to dt^2
    for (i in 1 until n - 1) {
        d[i] = (this[i + 1] - this[i - 1])
    }
    return d
}

/** Cumulative trapezoidal Integral */
fun DoubleArray.trapezoidalIntegral(initialOffset: Double = 0.0): DoubleArray {
    val n = size
    val out = DoubleArray(n)

    //initial offset so that we can for example drain energy if bpm is over that initial offset, making the integration negative
    var cumSum = initialOffset

    require(size > 0) { "Need at least 1 sample." }
    out[0] = cumSum
    for (i in 1 until n) {
        cumSum += 0.5 * (this[i - 1] + this[i])
        out[i] = cumSum
    }
    return out
}