package org.htwk.pacing.backend.predictor.stats

import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class NormalizationTests {
    //allowed deviation, even if there should technically be no deviation in the tests
    //helps with numerical precision issues
    private val delta: Double = 1e-3

    private val HR_MEAN: Double = 70.0
    private val HR_STANDARD_DEVIATION: Double = 30.0

    @Test
    fun emptyArrayReturnsEmptyArray() {
        val input = mk.ndarray(doubleArrayOf())
        input.normalize()

        val expected = doubleArrayOf()
        assertArrayEquals(input.toDoubleArray(), expected, delta)
    }

    @Test
    fun valueEqualToMeanNormalizesToZero() {
        val input = mk.ndarray(doubleArrayOf(100.0, 100.0, 100.0))
        input.normalize()

        val expected = doubleArrayOf(0.0, 0.0, 0.0)
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }

    @Test
    fun symmetricalValuesNormalizeToMinusOneAndOne() {
        val input = mk.ndarray(doubleArrayOf(HR_MEAN, HR_MEAN + HR_STANDARD_DEVIATION))
        input.normalize()

        val expected = doubleArrayOf(-1.0, 1.0)
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }

    @Test
    fun symmetricalValuesNormalizeToOneAndMinusOne() {
        val input = mk.ndarray(doubleArrayOf(HR_MEAN, HR_MEAN - HR_STANDARD_DEVIATION))
        input.normalize()

        val expected = doubleArrayOf(1.0, -1.0)
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }


    @Test
    fun multipleValuesNormalizeCorrectly() {
        val input = mk.ndarray(doubleArrayOf(10.0, 70.0, 100.0))
        val stddev = 37.4165738677

        input.normalize()

        val expected = doubleArrayOf(
            (10.0 - 60.0) / stddev, // ≈ -1.336
            (70.0 - 60.0) / stddev, // ≈ 0.267
            (100.0 - 60.0) / stddev  // ≈ 1.069
        )
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }

    @Test
    fun longFixedArrayNormalizesCorrectly() {
        val input = mk.ndarray(
            doubleArrayOf(
                40.0, 55.0, 70.0, 85.0, 100.0
            )
        )
        val distribution = StochasticDistribution(HR_MEAN, HR_STANDARD_DEVIATION)

        input.normalize(distribution)

        val expected = doubleArrayOf(
            -1.0,        // (40-70)/30
            -0.5,        // (55-70)/30
            0.0,         // (70-70)/30
            0.5,         // (85-70)/30
            1.0          // (100-70)/30
        )
        assertArrayEquals(expected, input.toDoubleArray(), delta)
    }

    @Test
    fun normalizeDenormalizeEqual() {
        val random = Random(1337)

        val raw = mk.ndarray(DoubleArray(50) { random.nextDouble(-100000.0, 100000.0) })
        val input = raw.copy()

        val distribution = input.normalize()
        val expectedDistribution = StochasticDistribution(-2712.369574, 62305.5474781)
        assertEquals(expectedDistribution.mean, distribution.mean, delta)
        assertEquals(expectedDistribution.stddev, distribution.stddev, delta)

        val inputNormalized = input.copy()

        inputNormalized.denormalize(distribution)

        assertArrayEquals(raw.toDoubleArray(), inputNormalized.toDoubleArray(), delta)
    }

    @Test
    fun normalizeDenormalizeEqualLargerArray() {
        val random = Random(1338)

        val raw = mk.ndarray(DoubleArray(1000) { random.nextDouble(-1000000.0, 1000000.0) })
        val input = raw.copy()

        val distribution = input.normalize()
        val expectedDistribution = StochasticDistribution(-9728.245, 594895.610)
        assertEquals(expectedDistribution.mean, distribution.mean, delta)
        assertEquals(expectedDistribution.stddev, distribution.stddev, delta)

        val inputNormalized = input.copy()

        inputNormalized.denormalize(distribution)

        assertArrayEquals(raw.toDoubleArray(), inputNormalized.toDoubleArray(), delta)
    }

    @Test
    fun normalizeSingleValue1() {
        val input = HR_MEAN

        val output = normalizeSingleValue(input, StochasticDistribution(HR_MEAN, HR_STANDARD_DEVIATION))

        val expected = 0.0
        assertEquals(expected, output, delta)
    }

    @Test
    fun normalizeSingleValue2() {
        val input = HR_MEAN + HR_STANDARD_DEVIATION * 0.5

        val output = normalizeSingleValue(input, StochasticDistribution(HR_MEAN, HR_STANDARD_DEVIATION))

        val expected = 0.5
        assertEquals(expected, output, delta)
    }
}