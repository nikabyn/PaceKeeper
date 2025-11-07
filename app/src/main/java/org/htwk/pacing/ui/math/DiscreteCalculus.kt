package org.htwk.pacing.ui.math

//SEE: ui#38 comment for explanation on this file

/**
 * Discrete calculus on uniformly sampled DoubleArray.
 * Assumes constant spacing dt > 0 between samples.
 *
 **/

/**
 * Calculates the discrete derivative of a uniformly sampled [DoubleArray].
 *
 * This function assumes a constant, unit spacing (dt=1) between samples.
 * To get the true derivative, the result should be divided by the actual `dt`.
 *
 * It uses second-order finite difference schemes for improved accuracy:
 * - **Central difference** for interior points: `d[i] = (y[i+1] - y[i-1]) / 2`
 * - **Forward difference** for the first point: `d[0] = (-3*y[0] + 4*y[1] - y[2]) / 2`
 * - **Backward difference** for the last point: `d[n-1] = (3*y[n-1] - 4*y[n-2] + y[n-3]) / 2`
 *
 * The implementation omits the division by 2 for performance, so the result is scaled by a factor of 2.
 * A special case is handled for an array of size 2, where a simple difference is used for both points.
 *
 * @receiver The [DoubleArray] of sampled data points. Must contain at least 2 elements.
 * @return A [DoubleArray] of the same size, containing the calculated derivatives at each point.
 * @throws IllegalArgumentException if the receiver array has less than 2 elements. We can't differentiate a single point.
 */
@Throws(IllegalArgumentException::class)
fun DoubleArray.discreteDerivative(): DoubleArray {
    require(size >= 2) { "Need at least 2 samples." }

    val n = size
    val d = DoubleArray(n)

    if (n == 2) {
        val s = (this[1] - this[0])
        d[0] = s; d[1] = s
        return d
    }

    // Second-order forward difference at the beginning
    d[0] = (-3.0 * this[0] + 4.0 * this[1] - this[2])
    // Second-order backward difference at the end
    d[n - 1] = (3.0 * this[n - 1] - 4.0 * this[n - 2] + this[n - 3])

    // Second-order central difference for interior points
    for (i in 1 until n - 1) {
        d[i] = (this[i + 1] - this[i - 1])
    }
    return d
}

/**
 * Calculates the cumulative trapezoidal integral of a uniformly sampled signal.
 *
 * This function computes the integral of the `DoubleArray` assuming a constant sampling interval `dt = 1.0`.
 * The integration is performed using the trapezoidal rule, accumulating the area at each step.
 *
 * The formula for the i-th element of the output array is:
 * `out[i] = initialOffset + Σ (0.5 * (this[j-1] + this[j]))` for j from 1 to i.
 *
 * @receiver The `DoubleArray` representing the uniformly sampled signal to integrate.
 * @param initialOffset The starting value for the integration (the value at index -1, conceptually). Defaults to 0.0.
 *                      This can be used to set an initial condition or a baseline for the integral.
 * @return A `DoubleArray` of the same size, where each element is the cumulative integral up to that point. The first element is always `initialOffset`.
 * @throws IllegalArgumentException if the receiver array is empty. We can't integrate an empty time series
 */
@Throws(IllegalArgumentException::class)
fun DoubleArray.discreteTrapezoidalIntegral(initialOffset: Double = 0.0): DoubleArray {
    val n = size
    val out = DoubleArray(n)

    //initial offset so that we can for example drain energy if bpm is over that initial offset, making the integration negative
    var cumSum = initialOffset

    require(this.isNotEmpty()) { "Need at least 1 sample." }
    out[0] = cumSum
    for (i in 1 until n) {
        cumSum += 0.5 * (this[i - 1] + this[i])
        out[i] = cumSum
    }
    return out
}