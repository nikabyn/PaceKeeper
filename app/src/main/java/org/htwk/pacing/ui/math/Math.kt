package org.htwk.pacing.ui.math

import androidx.annotation.FloatRange
import kotlinx.datetime.Instant
import kotlin.math.exp
import kotlin.math.sqrt
import kotlin.time.Duration

/**
 * Linearly interpolate between two floating point values.
 *
 * ## Examples
 *
 * ```kotlin
 * assertEq(interpolate(10.0, 0.0, 0.5f), 5.0)
 * assertEq(interpolate(0.0, 3.0, 1.0f), 3.0)
 * ```
 *
 * @param t interpolation factor, must be in range 0.0..<=1.0
 */
fun interpolate(a: Double, b: Double, @FloatRange(from = 0.0, to = 1.0) t: Float): Double {
    return a + t * (b - a)
}

/**
 * Maps a floating point value from one range to another. The ranges are inclusive.
 *
 * ## Examples
 *
 * ```kotlin
 * assertEq(remap(1.0, 0.0, 10.0, 0.0 50.0), 5.0)
 * assertEq(remap(0.0, 0.0, 1.0, 9.5 10.0), 9.5)
 * ```
 *
 * @param value floating point value to be mapped
 * @param lowIn lower bound input
 * @param highIn upper bound input
 * @param lowOut lower bound output
 * @param highOut upper bound output
 */
fun remap(value: Double, lowIn: Double, highIn: Double, lowOut: Double, highOut: Double): Double {
    if (lowIn == highIn) {
        println("Error: Invalid input range for remap: lowIn == highIn")
        return 0.0
    }
    return lowOut + (value - lowIn) * (highOut - lowOut) / (highIn - lowIn)
}

/**
 * A 2D vector with floating point coordinates.
 *
 * ## Examples
 *
 * ```kotlin
 * val v = Float2(3f, 4f)
 * val unit = v.normalize()
 * val scaled = v.scale(2f)
 * ```
 */
data class Float2D(val x: Float, val y: Float) {
    /**
     * Returns a unit vector pointing in the same direction.
     * If this vector is the zero vector (0, 0), it returns itself unchanged.
     *
     * ## Examples
     *
     * ```kotlin
     * val v = Float2(3f, 4f)
     * val unit = v.normalize() // length ≈ 1.0
     * ```
     */
    fun normalize(): Float2D {
        val length = sqrt(x * x + y * y)
        return if (length == 0f) this else Float2D(x / length, y / length)
    }

    /**
     * Scales the vector by a scalar factor.
     * Multiplies both x and y components by the given factor.
     *
     * ## Examples
     *
     * ```kotlin
     * val v = Float2(1f, -2f)
     * val doubled = v.scale(2f) // Float2(2f, -4f)
     * ```
     */
    fun scale(factor: Float): Float2D {
        return Float2D(x * factor, y * factor)
    }
}

/**
 * Rounds a time instant down to a given resolution.
 *
 * ## Examples
 *
 * ```kotlin
 * roundInstantToResolution(Clock.System.now(), 10.minutes) //will round down to last 10 minute mark
 * ```
 *
 * @param instant time point to be rounded
 * @param resolution resolution to be rounded to
 */
fun roundInstantToResolution(instant: Instant, resolution: Duration): Instant {
    val truncatedEpochMillis =
        instant.toEpochMilliseconds() - (instant.toEpochMilliseconds() % resolution.inWholeMilliseconds)
    return Instant.fromEpochMilliseconds(truncatedEpochMillis)
}

/**
 * Calculates the sigmoid function in a numerically stable way.
 *
 * The sigmoid function, `S(x) = 1 / (1 + e^-x)`, maps any real-valued number
 * into the range (0, 1). This implementation avoids floating-point overflow
 * issues that can occur with large positive or negative input values by using
 * two different but mathematically equivalent formulas depending on the sign of `x`.
 *
 * - For `x >= 0`, it computes `1.0 / (1.0 + exp(-x))`.
 * - For `x < 0`, it computes `exp(x) / (1.0 + exp(x))`.
 *
 * @param x The input value.
 * @return The result of the sigmoid function, a value between 0.0 and 1.0.
 */
fun sigmoidStable(x: Double): Double {
    if (x >= 0.0) {
        val z = exp(-x)
        return 1.0 / (1.0 + z)
    } else {
        val z = exp(x)
        return z / (1.0 + z)
    }
}