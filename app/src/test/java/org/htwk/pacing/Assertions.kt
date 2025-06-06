package org.htwk.pacing

import org.junit.Assert.assertTrue
import kotlin.math.abs

fun assertFloat(value: Float, expected: Float, epsilon: Float) {
    assertTrue(
        "Expected <${expected}> +-${epsilon}, actual <${value}>.",
        abs(value - expected) <= epsilon,
    )
}

fun assertDouble(value: Double, expected: Double, epsilon: Double) {
    assertTrue(
        "Expected <${expected}> +-${epsilon}, actual <${value}>.",
        abs(value - expected) <= epsilon,
    )
}