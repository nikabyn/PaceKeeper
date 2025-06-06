package org.htwk.pacing.math

fun interpolate(a: Float, b: Float, t: Float): Float {
    return a + t * (b - a)
}

fun remap(value: Float, lowIn: Float, highIn: Float, lowOut: Float, highOut: Float): Float {
    return lowOut + (value - lowIn) * (highOut - lowOut) / (highIn - lowIn)
}