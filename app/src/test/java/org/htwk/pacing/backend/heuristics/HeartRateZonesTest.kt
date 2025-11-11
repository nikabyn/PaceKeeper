package org.htwk.pacing.backend.heuristics

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class HeartRateZonesTest {

    @Test
    fun `calculateMaxHeartRate for male`() {
        // Given
        val age = 30
        val gender = HeartRateZones.Gender.MALE

        // When
        val result = HeartRateZones.calculateMaxHeartRate(age, gender)

        // Then
        val expected = 223.0 - (0.9 * 30) // 223 - 27 = 196
        assertEquals(expected, result)
    }

    @Test
    fun `calculateMaxHeartRate for female`() {
        // Given
        val age = 25
        val gender = HeartRateZones.Gender.FEMALE

        // When
        val result = HeartRateZones.calculateMaxHeartRate(age, gender)

        // Then
        val expected = 223.0 - 25.0 // 198
        assertEquals(expected, result)
    }

    @Test
    fun calculateAnaerobicThreshold() {
        // Given
        val maxHeartRate = 200.0

        // When
        val result = HeartRateZones.calculateAnaerobicThreshold(maxHeartRate)

        // Then
        val expected = 200.0 * 0.55 // 110
        assertEquals(expected, result)
    }

    @Test
    fun `calculateZones for female with resting heart rate 50`() {
        // Given
        val input = HeartRateZones.HeartRateInput(
            age = 23,
            gender = HeartRateZones.Gender.FEMALE,
            restingHeartRate = 50
        )

        // When
        val result = HeartRateZones.calculateZones(input)

        // Then
        // Expected calculations:
        // Max HR: 223 - 23 = 200
        // Anaerobic Threshold: 200 * 0.55 = 110
        // Health Zone Lower: max(30, 50*0.9=45) = 45
        // Health Zone Upper: 50*1.10=55
        // Recovery Zone Upper: 50*1.20=60
        // Exertion Zone Upper: 110

        assertEquals(200.0, result.maxHeartRate.roundToInt())
        assertEquals(110, result.anaerobicThreshold.roundToInt())

        // Check zones are properly defined and ascending
        assertEquals(0..55, result.healthZone) // Health zone from 0 to 55
        assertEquals(56..60, result.recoveryZone) // Recovery zone from 56 to 60
        assertEquals(61..110, result.exertionZone) // Exertion zone from 61 to 109 (below threshold)

        // Verify zones don't overlap and are in correct order
        assertTrue(result.healthZone.last < result.recoveryZone.first)
        assertTrue(result.recoveryZone.last < result.exertionZone.first)
        assertTrue(result.exertionZone.last < result.anaerobicThreshold)
    }

    @Test
    fun `calculateZones for male with resting heart rate 60`() {
        // Given
        val input = HeartRateZones.HeartRateInput(
            age = 40,
            gender = HeartRateZones.Gender.MALE,
            restingHeartRate = 60
        )

        // When
        val result = HeartRateZones.calculateZones(input)

        // Then
        // Expected calculations:
        // Max HR: 223 - (0.9 * 40) = 223 - 36 = 187
        // Anaerobic Threshold: 187 * 0.55 = 102.85 ≈ 102
        // Health Zone Lower: max(30, 60*0.9=54) = 54
        // Health Zone Upper: 60*1.10=66
        // Recovery Zone Upper: 60*1.20=72
        // Exertion Zone Upper: 102

        assertEquals(187.0, result.maxHeartRate, 0.01)
        assertEquals(102.85, result.anaerobicThreshold, 0.01)

        // Check zones
        assertEquals(0..66, result.healthZone)
        assertEquals(67..72, result.recoveryZone)
        assertEquals(73..102, result.exertionZone) // Should end at 101 (below threshold 102.85)

        // Verify structure
        assertTrue(result.healthZone.last < result.recoveryZone.first)
        assertTrue(result.recoveryZone.last < result.exertionZone.first)
    }

    @Test
    fun `calculateZones with very low resting heart rate`() {
        // Given
        val input = HeartRateZones.HeartRateInput(
            age = 25,
            gender = HeartRateZones.Gender.FEMALE,
            restingHeartRate = 40
        )

        // When
        val result = HeartRateZones.calculateZones(input)

        // Then
        // Health Zone Lower should be at least 30 due to maxOf(30, 40*0.9=36)
        assertEquals(0..44, result.healthZone) // 40*1.10=44
        assertEquals(45..48, result.recoveryZone) // 40*1.20=48
        assertEquals(49..108, result.exertionZone) // (223-25)*0.55=108.9 → 108

        // Verify all zones are valid
        assertTrue(result.healthZone.first >= 0)
        assertTrue(result.healthZone.last >= result.healthZone.first)
        assertTrue(result.recoveryZone.last >= result.recoveryZone.first)
        assertTrue(result.exertionZone.last >= result.exertionZone.first)
    }

    @Test
    fun `calculateZones with high resting heart rate`() {
        // Given
        val input = HeartRateZones.HeartRateInput(
            age = 50,
            gender = HeartRateZones.Gender.MALE,
            restingHeartRate = 80
        )

        // When
        val result = HeartRateZones.calculateZones(input)

        // Then
        // Max HR: 223 - (0.9 * 50) = 223 - 45 = 178
        // Anaerobic Threshold: 178 * 0.55 = 97.9 ≈ 97
        assertEquals(178.0, result.maxHeartRate, 0.01)
        assertEquals(97.9, result.anaerobicThreshold, 0.01)

        assertEquals(0..88, result.healthZone) // 80*1.10=88
        assertEquals(89..96, result.recoveryZone) // 80*1.20=96
        assertEquals(97..97, result.exertionZone) // This case might need adjustment

        // Special case: exertion zone might be empty if threshold is too close
        assertTrue(result.exertionZone.last >= result.exertionZone.first)
    }

    @Test
    fun `zones should not exceed max heart rate`() {
        // Given
        val input = HeartRateZones.HeartRateInput(
            age = 20,
            gender = HeartRateZones.Gender.FEMALE,
            restingHeartRate = 70
        )

        // When
        val result = HeartRateZones.calculateZones(input)

        // Then - all zones should be below max heart rate
        assertTrue(result.healthZone.last <= result.maxHeartRate)
        assertTrue(result.recoveryZone.last <= result.maxHeartRate)
        assertTrue(result.exertionZone.last <= result.maxHeartRate)
    }

    @Test
    fun `zones should have proper progression`() {
        // Given
        val input = HeartRateZones.HeartRateInput(
            age = 30,
            gender = HeartRateZones.Gender.MALE,
            restingHeartRate = 55
        )

        // When
        val result = HeartRateZones.calculateZones(input)

        // Then - zones should be in correct order without gaps
        assertTrue(result.healthZone.last + 1 == result.recoveryZone.first)
        assertTrue(result.recoveryZone.last + 1 == result.exertionZone.first)
    }
}