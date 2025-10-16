package org.htwk.pacing.backend.heuristics

import org.junit.Assert.assertEquals
import org.junit.Test

class EnergyLevelCalculatorTest {

    @Test
    fun calculateNewEnergyLevel_1_reducesCorrectly() {
        val result = calculateNewEnergyLevel(100.0, 1)
        assertEquals(95.0, result, 0.01)
    }

    @Test
    fun calculateNewEnergyLevel_2_reducesCorrectly() {
        val result = calculateNewEnergyLevel(100.0, 2)
        assertEquals(80.0, result, 0.01)
    }

    @Test
    fun calculateNewEnergyLevel_3_reducesCorrectly() {
        val result = calculateNewEnergyLevel(100.0, 3)
        assertEquals(60.0, result, 0.01)
    }

    @Test
    fun calculateNewEnergyLevel_severity0_doesNotThrowException() {
        val result = calculateNewEnergyLevel(100.0, 0)
        assertEquals(95.0, result, 0.01)
    }

    @Test
    fun calculateNewEnergyLevel_severity4_doesNotThrowException() {
        val result = calculateNewEnergyLevel(100.0, 4)
        assertEquals(60.0, result, 0.01)
    }
}
