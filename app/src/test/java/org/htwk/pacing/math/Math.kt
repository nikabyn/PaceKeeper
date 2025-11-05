package org.htwk.pacing.math

import org.htwk.pacing.ui.math.Float2D
import org.htwk.pacing.ui.math.interpolate
import org.htwk.pacing.ui.math.remap
import org.htwk.pacing.ui.math.sigmoidStable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
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

    private fun assertFloat2(actual: Float2D, expected: Float2D, epsilon: Float) {
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

        assertFloat2(Float2D(1f, 0f).scale(2f), Float2D(2f, 0f), epsilon)
        assertFloat2(Float2D(0f, 1f).scale(-1f), Float2D(0f, -1f), epsilon)
        assertFloat2(Float2D(3f, 4f).scale(0.5f), Float2D(1.5f, 2f), epsilon)
        assertFloat2(Float2D(0f, 0f).scale(100f), Float2D(0f, 0f), epsilon)
    }

    @Test
    fun normalize_returnsUnitVector() {
        val epsilon = 1e-5f

        val normalized = Float2D(3f, 4f).normalize()
        val length = sqrt(normalized.x * normalized.x + normalized.y * normalized.y)
        assertFloat(length, 1f, epsilon)
    }

    @Test
    fun normalize_preservesDirection() {
        val epsilon = 1e-5f

        val original = Float2D(2f, 2f)
        val normalized = original.normalize()
        val factor = original.x / normalized.x
        assertFloat2(normalized.scale(factor), original, epsilon)
    }

    @Test
    fun normalize_zeroVector_noCrash() {
        val epsilon = 1e-5f

        val zero = Float2D(0f, 0f)
        assertEquals(zero, zero.normalize())
    }

    @Test
    fun `sigmoidStable test zero input`() {
        // Test case: Zero input
        // The function should be centered at x=0, resulting in 0.5.
        assertEquals("Sigmoid of 0.0 should be 0.5", 0.5, sigmoidStable(0.0), 1e-9)
    }

    @Test
    fun `sigmoidStable regular inputs`() {
        // Test case: Standard positive input
        // Check the function's output for a typical positive value.
        val expectedPositive = 1.0 / (1.0 + exp(-1.0))
        assertEquals("Sigmoid of 1.0", expectedPositive, sigmoidStable(1.0), 1e-9)

        // Test case: Standard negative input
        // Check the function's output for a typical negative value.
        val expectedNegative = exp(-1.0) / (1.0 + exp(-1.0))
        assertEquals("Sigmoid of -1.0", expectedNegative, sigmoidStable(-1.0), 1e-9)
    }

    @Test
    fun `sigmoidStable test handling of large input values`() {
        // Test case: Large positive inputs (approaching 1.0)
        // Verifies the upper limit and stability for large values, including infinity.
        assertEquals("Sigmoid of 50.0 should approach 1.0", 1.0, sigmoidStable(50.0), 1e-9)
        assertEquals(
            "Sigmoid of Double.MAX_VALUE should be 1.0",
            1.0,
            sigmoidStable(Double.MAX_VALUE),
            0.0
        )
        assertEquals(
            "Sigmoid of Double.POSITIVE_INFINITY should be 1.0",
            1.0,
            sigmoidStable(Double.POSITIVE_INFINITY),
            0.0
        )

        // Test case: Large negative inputs (approaching 0.0)
        // Verifies the lower limit and stability for large negative values, including negative infinity.
        assertEquals("Sigmoid of -50.0 should approach 0.0", 0.0, sigmoidStable(-50.0), 1e-9)
        assertEquals(
            "Sigmoid of Double.NEGATIVE_INFINITY should be 0.0",
            0.0,
            sigmoidStable(Double.NEGATIVE_INFINITY),
            0.0
        )
    }

    @Test
    fun `sigmoidStable test NaN input`() {
        // Test case: NaN input
        // Ensures correct handling of invalid floating-point values.
        assertTrue("Sigmoid of NaN should be NaN", sigmoidStable(Double.NaN).isNaN())
    }
}