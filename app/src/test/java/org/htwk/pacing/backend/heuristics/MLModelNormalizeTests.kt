package org.htwk.pacing.backend.heuristics

import org.htwk.pacing.backend.mlmodel.MLModel
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class MLModelNormalizeTests {
    //allowed deviation, even if there should technically be no deviation in the tests
    private val delta = 1e-6f

    @Test
    fun emptyArrayReturnsEmptyArray() {
        val input = floatArrayOf()
        val result = MLModel.normalize(input)
        assertArrayEquals(floatArrayOf(), result, delta)
    }

    @Test
    fun valueEqualToMeanNormalizesToZero() {
        val input = floatArrayOf(MLModel.HR_MEAN)
        val expected = floatArrayOf(0f)
        val result = MLModel.normalize(input)
        assertArrayEquals(expected, result, delta)
    }

    @Test
    fun oneStandardDeviationAboveMeanNormalizesToOne() {
        val value = MLModel.HR_MEAN + MLModel.HR_STANDARD_DEVIATION
        val input = floatArrayOf(value)
        val expected = floatArrayOf(1f)
        val result = MLModel.normalize(input)
        assertArrayEquals(expected, result, delta)
    }

    @Test
    fun oneStandardDeviationBelowMeanNormalizesToMinusOne() {
        val value = MLModel.HR_MEAN - MLModel.HR_STANDARD_DEVIATION
        val input = floatArrayOf(value)
        val expected = floatArrayOf(-1f)
        val result = MLModel.normalize(input)
        assertArrayEquals(expected, result, delta)
    }

    @Test
    fun multipleValuesNormalizeCorrectly() {
        // -2 SD, 0 SD, +0.5 SD
        val inputs = floatArrayOf(
            MLModel.HR_MEAN - MLModel.HR_STANDARD_DEVIATION * 2f,  // should normalize to -2
            MLModel.HR_MEAN,                                      // should normalize to  0
            MLModel.HR_MEAN + MLModel.HR_STANDARD_DEVIATION * 0.5f // should normalize to  0.5
        )
        val expected = floatArrayOf(-2f, 0f, 0.5f)
        val result = MLModel.normalize(inputs)
        assertArrayEquals(expected, result, delta)
    }
}