package org.htwk.pacing.math

import androidx.annotation.FloatRange

fun interpolate(a: Float, b: Float, @FloatRange(from = 0.0, to = 1.0)t: Float): Float {
    return a + t * (b - a)
}

fun remap(value: Float, lowIn: Float, highIn: Float, lowOut: Float, highOut: Float): Float {
    if (lowIn == highIn) {
        println("Error: Invalid input range for remap: lowIn == highIn")
        return 0f
    }
    return lowOut + (value - lowIn) * (highOut - lowOut) / (highIn - lowIn)
}