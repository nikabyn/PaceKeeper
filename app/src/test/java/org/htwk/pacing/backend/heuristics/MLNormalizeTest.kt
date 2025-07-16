package org.htwk.pacing.backend.mlmodel

import org.junit.Test
import org.junit.Assert.*
import kotlin.math.sqrt

class MLModelNormalizeTest {

    @Test
    fun testNormalizeWithNonZeroVariance() {
        // Arrange
        val input = floatArrayOf(60f, 70f, 80f, 90f, 100f)
        val expectedMean = 80f
        val expectedStdDev = sqrt(200f) // sqrt(((60-80)² + (70-80)² + (80-80)² + (90-80)² + (100-80)²)/5)

        // Act
        val (normalized, stats) = mlNormalize(input)

        // Assert
        assertEquals("Mean calculation incorrect", expectedMean, stats.mean, 0.001f)
        assertEquals("Standard deviation calculation incorrect",
            expectedStdDev, stats.standardDeviation, 0.001f)

        // Verify normalized values
        val expectedNormalized = floatArrayOf(
            (60f - expectedMean) / expectedStdDev,
            (70f - expectedMean) / expectedStdDev,
            (80f - expectedMean) / expectedStdDev,
            (90f - expectedMean) / expectedStdDev,
            (100f - expectedMean) / expectedStdDev
        )

        for (i in expectedNormalized.indices) {
            assertEquals("Normalized value at index $i incorrect",
                expectedNormalized[i], normalized[i], 0.001f)
        }
    }

    @Test
    fun testNormalizeWithZeroVariance() {
        // Arrange
        val input = floatArrayOf(80f, 80f, 80f, 80f, 80f)
        val expectedMean = 80f
        val expectedStdDev = 0.000001f // Should use the safety value

        // Act
        val (normalized, stats) = mlNormalize(input)

        // Assert
        assertEquals("Mean calculation incorrect", expectedMean, stats.mean, 0.001f)
        assertEquals("Standard deviation should use safety value",
            expectedStdDev, stats.standardDeviation, 0.001f)

        // All normalized values should be 0 (except for floating point precision)
        for (value in normalized) {
            assertEquals("Normalized value should be 0 for constant input",
                0f, value, 0.001f)
        }
    }

    @Test
    fun testNormalizeWithSingleValue() {
        // Arrange
        val input = floatArrayOf(75f)
        val expectedMean = 75f
        val expectedStdDev = 0.000001f // Should use the safety value

        // Act
        val (normalized, stats) = mlNormalize(input)

        // Assert
        assertEquals("Mean calculation incorrect", expectedMean, stats.mean, 0.001f)
        assertEquals("Standard deviation should use safety value",
            expectedStdDev, stats.standardDeviation, 0.001f)
        assertEquals("Single normalized value should be 0",
            0f, normalized[0], 0.001f)
    }
}