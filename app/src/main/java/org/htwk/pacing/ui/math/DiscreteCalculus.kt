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

fun centeredMovingAverage(data: DoubleArray, window: Int): DoubleArray {
    require(window > 0 && window % 2 == 0) {
        "Window must be a positive even number for centered average"
    }

    val n = data.size
    val half = window / 2
    val result = DoubleArray(n)

    // Prefix sums
    val prefix = DoubleArray(n + 1)
    for (i in 0 until n) prefix[i + 1] = prefix[i] + data[i]

    for (i in 0 until n) {
        val start = i - half
        val end = start + window

        if (start < 0) {
            result[i] = data.first()
            continue
        } else if (end > n) {
            result[i] = data.last()
            continue
        }

        result[i] = (prefix[end] - prefix[start]) / window
    }

    return result
}

fun DoubleArray.causalMovingAverage(window: Int): DoubleArray {
    val smoothed = DoubleArray(size)
    var sum = 0.0
    for (i in indices) {
        sum += this[i]
        if (i >= window) sum -= this[i - window]
        smoothed[i] = sum / (i + 1).coerceAtMost(window)
    }
    return smoothed
}

/**
 * Applies an Exponential Moving Average (Low-Pass Filter).
 * Reduces lag compared to SMA by weighting recent values higher.
 * * @param alpha Smoothing factor (0.0 - 1.0).
 * Higher alpha = Less smoothing, Faster reaction (Less lag).
 * Lower alpha  = More smoothing, Slower reaction (More lag).
 * Typical value for "Window 3 equivalent": ~0.5
 */
fun DoubleArray.causalExponentialMovingAverage(alpha: Double): DoubleArray {
    if (isEmpty()) return this
    val smoothed = DoubleArray(size)

    // Initialize with the first value
    var currentLevel = this[0]
    smoothed[0] = currentLevel

    for (i in 1 until size) {
        // Formula: NewValue = (Raw * Alpha) + (OldSmoothed * (1 - Alpha))
        currentLevel = (this[i] * alpha) + (currentLevel * (1.0 - alpha))
        smoothed[i] = currentLevel
    }
    return smoothed
}