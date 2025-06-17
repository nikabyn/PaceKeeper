package org.htwk.pacing.math

import org.htwk.pacing.ui.math.Float2
import org.htwk.pacing.ui.math.interpolate
import org.htwk.pacing.ui.math.remap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class Math {
    private fun assertFloat(value: Float, expected: Float, epsilon: Float) {
        assertTrue(
            "Expected <${expected}> +-${epsilon}, actual <${value}>.",
            abs(value - expected) <= epsilon,
        )
    }

    private fun assertDouble(value: Double, expected: Double, epsilon: Double) {
        assertTrue(
            "Expected <${expected}> +-${epsilon}, actual <${value}>.",
            abs(value - expected) <= epsilon,
        )
    }

    private fun assertFloat2(actual: Float2, expected: Float2, epsilon: Float) {
        assertFloat(actual.x, expected.x, epsilon)
        assertFloat(actual.y, expected.y, epsilon)
    }

    @Test
    fun interpolate_isValid() {
        val epsilon = 1e-5

        assertDouble(interpolate(0.0, 1.0, 0.5f), 0.5, epsilon)
        assertDouble(interpolate(1.0, 0.0, 0.5f), 0.5, epsilon)
        assertDouble(interpolate(0.0, 50.0, 0.2f), 10.0, epsilon)
        assertDouble(interpolate(50.0, 0.0, 0.2f), 40.0, epsilon)
        assertDouble(interpolate(10.0, 100.0, 0.4f), 46.0, epsilon)
        assertDouble(interpolate(100.0, 10.0, 0.6f), 46.0, epsilon)
        assertDouble(interpolate(0.0, 1.0, 10f), 10.0, epsilon)
        assertDouble(interpolate(1.0, 0.0, 10f), -9.0, epsilon)
        assertDouble(interpolate(1.0, 1.0, 0.5f), 1.0, epsilon)
        assertDouble(interpolate(100.0, 100.0, 1f), 100.0, epsilon)
        assertDouble(interpolate(0.0, 1.0, 0f), 0.0, epsilon)
    }

    @Test
    fun remap_isValid() {
        val epsilon = 1e-5

        assertDouble(remap(0.0, 0.0, 1.0, 10.0, 100.0), 10.0, epsilon)
        assertDouble(remap(0.0, 0.0, 1.0, 0.0, 0.0), 0.0, epsilon)
        assertDouble(remap(0.0, 0.0, 0.0, 10.0, 100.0), 0.0, epsilon)
        assertDouble(remap(0.0, 0.0, 1.0, 100.0, 10.0), 100.0, epsilon)
        assertDouble(remap(0.0, 0.0, 1.0, 1.0, 10000.0), 1.0, epsilon)
        assertDouble(remap(8.0, 0.0, 1.0, 0.0, 100.0), 800.0, epsilon)
        assertDouble(remap(-9.0, 0.0, 1.0, 0.0, -100.0), 900.0, epsilon)
        assertDouble(remap(12.0, 0.0, 20.0, 0.0, 10.0), 6.0, epsilon)
        assertDouble(remap(90.0, 0.0, 10.0, 1.0, 2.0), 10.0, epsilon)
        assertDouble(remap(0.5, 0.0, 1.0, 0.0, 3.0), 1.5, epsilon)
        assertDouble(remap(1.0, 0.0, 4.0, 0.0, 1.0), 0.25, epsilon)
        assertDouble(remap(8.0, 0.0, 80.0, 80.0, 0.0), 72.0, epsilon)
    }

    @Test
    fun scale_scalesCorrectly() {
        val epsilon = 1e-5f

        assertFloat2(Float2(1f, 0f).scale(2f), Float2(2f, 0f), epsilon)
        assertFloat2(Float2(0f, 1f).scale(-1f), Float2(0f, -1f), epsilon)
        assertFloat2(Float2(3f, 4f).scale(0.5f), Float2(1.5f, 2f), epsilon)
        assertFloat2(Float2(0f, 0f).scale(100f), Float2(0f, 0f), epsilon)
    }

    @Test
    fun normalize_returnsUnitVector() {
        val epsilon = 1e-5f

        val normalized = Float2(3f, 4f).normalize()
        val length = sqrt(normalized.x * normalized.x + normalized.y * normalized.y)
        assertFloat(length, 1f, epsilon)
    }

    @Test
    fun normalize_preservesDirection() {
        val epsilon = 1e-5f

        val original = Float2(2f, 2f)
        val normalized = original.normalize()
        val factor = original.x / normalized.x
        assertFloat2(normalized.scale(factor), original, epsilon)
    }

    @Test
    fun normalize_zeroVector_noCrash() {
        val epsilon = 1e-5f

        val zero = Float2(0f, 0f)
        assertEquals(zero, zero.normalize())
    }
}