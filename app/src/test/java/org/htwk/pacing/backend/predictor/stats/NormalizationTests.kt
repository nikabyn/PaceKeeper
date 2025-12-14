package org.htwk.pacing.backend.predictor.stats

import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class NormalizationTests {
    //allowed deviation, even if there should technically be no deviation in the tests
    private val delta: Double = 1e-6

    private val HR_MEAN: Double = 70.0
    private val HR_STANDARD_DEVIATION: Double = 30.0

    @Test
    fun emptyArrayReturnsEmptyArray() {
        val input = mk.ndarray(doubleArrayOf())
        normalize(input)

        val expected = doubleArrayOf()
        assertArrayEquals(input.toDoubleArray(), expected, delta)
    }

    @Test
    fun valueEqualToMeanNormalizesToZero() {
        val input = mk.ndarray(doubleArrayOf(100.0, 100.0, 100.0))
        normalize(input)

        val expected = doubleArrayOf(0.0, 0.0, 0.0)
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }

    @Test
    fun oneStandardDeviationAboveMeanNormalizesToOne() {
        val input = mk.ndarray(doubleArrayOf(HR_MEAN, HR_MEAN + HR_STANDARD_DEVIATION))
        normalize(input)

        val expected = doubleArrayOf(-1.0, 1.0)
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }

    @Test
    fun oneStandardDeviationBelowMeanNormalizesToMinusOne() {
        val input = mk.ndarray(doubleArrayOf(HR_MEAN, HR_MEAN - HR_STANDARD_DEVIATION))
        normalize(input)

        val expected = doubleArrayOf(1.0, -1.0)
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }

    @Test
    fun multipleValuesNormalizeCorrectly() {
        // -2 SD, 0 SD, +0.5 SD
        val input = mk.ndarray(
            doubleArrayOf(
                HR_MEAN - HR_STANDARD_DEVIATION * 2.0,
                HR_MEAN,
                HR_MEAN + HR_STANDARD_DEVIATION * 1.0
            )
        )
        val stochasticDistribution = normalize(input)
        assertEquals(stochasticDistribution, StochasticDistribution(0.5))

        val expected = doubleArrayOf(-2.0, 0.0, 0.5)
        assertArrayEquals(input.toDoubleArray(), expected, delta)
    }
}