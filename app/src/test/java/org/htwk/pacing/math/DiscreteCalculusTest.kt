package org.htwk.pacing.math

import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.trapezoidalIntegral
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class DiscreteCalculusTest {

    @Test(expected = IllegalArgumentException::class)
    fun testDerivativeZuWenigWerte() {
        doubleArrayOf(1.0).discreteDerivative()
    }

    @Test
    fun testDerivativeZweiWerte() {
        val input = doubleArrayOf(2.0, 5.0)
        val expected = doubleArrayOf(3.0, 3.0)
        val result = input.discreteDerivative()
        assertArrayEquals(expected, result, 1e-9)
    }

    @Test
    fun testDerivativeKonstantesSignal() {
        val input = doubleArrayOf(1.0, 1.0, 1.0, 1.0)
        val result = input.discreteDerivative()
        result.forEach { assertEquals(0.0, it, 1e-9) }
    }

    @Test
    fun testDerivativeLinearerVerlauf() {
        // y = 2x → Ableitung = 2
        val input = doubleArrayOf(0.0, 2.0, 4.0, 6.0, 8.0)
        val result = input.discreteDerivative()
        result.forEach { assertEquals(2.0, it, 1e-9) }
    }

    @Test
    fun testIntegralKonstantesSignal() {
        val input = doubleArrayOf(1.0, 1.0, 1.0, 1.0)
        val expected = doubleArrayOf(0.0, 1.0, 2.0, 3.0)
        val result = input.trapezoidalIntegral()
        assertArrayEquals(expected, result, 1e-9)
    }

    @Test
    fun testIntegralMitInitialOffset() {
        val input = doubleArrayOf(1.0, 1.0, 1.0)
        val expected = doubleArrayOf(5.0, 6.0, 7.0)
        val result = input.trapezoidalIntegral(initialOffset = 5.0)
        assertArrayEquals(expected, result, 1e-9)
    }

    @Test
    fun testIntegralLinearerVerlauf() {
        val input = doubleArrayOf(0.0, 1.0, 2.0, 3.0)
        val expected = doubleArrayOf(0.0, 0.5, 2.0, 4.5)
        val result = input.trapezoidalIntegral()
        assertArrayEquals(expected, result, 1e-9)
    }

    @Test
    fun testSinusAbleitung() {
        val n = 90
        val sinValues: DoubleArray = DoubleArray(n) { i ->
            val x = 2 * PI * i / n
            sin(x)
        }

        val cosValues: DoubleArray = DoubleArray(n) { i ->
            val x = 2 * PI * i / n
            cos(x) * (4 * PI / n) //we need to scale here because the higher the sample rate, the lower the change per sample
        }


        val input = sinValues
        val expected = cosValues


        val result= input.discreteDerivative()

        //explanation: doing a discrete derivative always leads an initial offset, because we can't
        //know the derivative at the first point, the discreteDerivate() tries to estimate it, but we need to leave a little error margin
        //also: we observed that the error doesn't accumulate over time, there's simply a small initial offset
        assertArrayEquals(expected, result, 0.02)
    }

    @Test
    fun testCosAufleitung() {
        val n = 20
        val cosValues: DoubleArray = DoubleArray(n) { i ->
            val x = 2 * PI * i / n
            cos(x)
        }
        val input = cosValues
        val sinValues: DoubleArray = DoubleArray(n) { i ->
            val x = 2 * PI * i / n
            sin(x) * PI
        }
        val expected = sinValues

        val result = input.trapezoidalIntegral()
        assertArrayEquals(expected, result, 0.02)
    }
}
