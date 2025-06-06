package org.htwk.pacing.math

import androidx.annotation.FloatRange

fun interpolate(a: Double, b: Double, @FloatRange(from = 0.0, to = 1.0) t: Float): Double {
    return a + t * (b - a)
}

fun remap(value: Double, lowIn: Double, highIn: Double, lowOut: Double, highOut: Double): Double {
    if (lowIn == highIn) {
        println("Error: Invalid input range for remap: lowIn == highIn")
        return 0.0
    }
    return lowOut + (value - lowIn) * (highOut - lowOut) / (highIn - lowIn)
}