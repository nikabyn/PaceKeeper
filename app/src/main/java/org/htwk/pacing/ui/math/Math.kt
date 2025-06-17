package org.htwk.pacing.ui.math

import androidx.annotation.FloatRange
import kotlin.math.sqrt

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

data class Float2(val x: Float, val y: Float) {
    fun normalize(): Float2 {
        val length = sqrt(x * x + y * y)
        return Float2(x / length, y / length)
    }

    fun scale(factor: Float): Float2 {
        return Float2(x * factor, y * factor)
    }
}
