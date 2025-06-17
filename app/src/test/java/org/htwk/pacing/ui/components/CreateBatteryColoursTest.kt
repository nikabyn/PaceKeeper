package org.htwk.pacing.ui.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert
import org.junit.Test

class CreateBatteryColoursTest {

    @Test
    fun testDefaultColorsWithAllSegments() {
        val result = createBatteryColors(6)
        val expected = listOf(
            Color.Companion.Green,
            Color.Companion.Green,
            Color.Companion.Yellow,
            Color.Companion.Yellow,
            Color.Companion.Red,
            Color.Companion.Red
        )
        Assert.assertEquals(expected, result)
    }

    @Test
    fun testColorOverrides() {
        val result = createBatteryColors(6, listOf(1, 3))
        val expected = listOf(
            Color.Companion.Green,
            Color.Companion.White,
            Color.Companion.Yellow,
            Color.Companion.White,
            Color.Companion.Red,
            Color.Companion.Red
        )
        Assert.assertEquals(expected, result)
    }

    @Test
    fun testSegmentsLimit() {
        val result = createBatteryColors(4)
        val expected = listOf(
            Color.Companion.Green,
            Color.Companion.Green,
            Color.Companion.Yellow,
            Color.Companion.Yellow
        )
        Assert.assertEquals(expected, result)
    }

    @Test
    fun testOverrideOutOfRangeIgnored() {
        val result = createBatteryColors(3, listOf(10))
        val expected = listOf(Color.Companion.Green, Color.Companion.Green, Color.Companion.Yellow)
        Assert.assertEquals(expected, result)
    }

    @Test
    fun testNoSegmentsReturnsEmptyList() {
        val result = createBatteryColors(0)
        Assert.assertTrue(result.isEmpty())
    }
}