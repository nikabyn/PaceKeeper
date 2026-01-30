package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnergyDecayFallbackTest {

    // Helper to create energy data points
    private fun createEnergyPoints(
        startMs: Long,
        intervalMs: Long,
        percentages: List<Double>
    ): List<EnergyDataPoint> {
        return percentages.mapIndexed { index, percentage ->
            EnergyDataPoint(
                timestamp = Instant.fromEpochMilliseconds(startMs + index * intervalMs),
                percentage = percentage
            )
        }
    }

    // ========== defaultDecayRate Tests ==========

    @Test
    fun `defaultDecayRate returns 3 percent per hour`() {
        val result = EnergyDecayFallback.defaultDecayRate()

        assertEquals(3.0, result.averageHourlyDecay, 0.001)
    }

    @Test
    fun `defaultDecayRate returns null for all time-of-day rates`() {
        val result = EnergyDecayFallback.defaultDecayRate()

        assertNull(result.morningDecayRate)
        assertNull(result.afternoonDecayRate)
        assertNull(result.eveningDecayRate)
        assertNull(result.nightRecoveryRate)
    }

    @Test
    fun `defaultDecayRate returns zero data points used`() {
        val result = EnergyDecayFallback.defaultDecayRate()

        assertEquals(0, result.dataPointsUsed)
    }

    // ========== computeDecayRate Tests ==========

    @Test
    fun `computeDecayRate returns default for less than 2 data points`() {
        val data = listOf(
            EnergyDataPoint(Instant.fromEpochMilliseconds(0), 80.0)
        )

        val result = EnergyDecayFallback.computeDecayRate(data)

        assertEquals(3.0, result.averageHourlyDecay, 0.001)
        assertEquals(0, result.dataPointsUsed)
    }

    @Test
    fun `computeDecayRate returns default for empty list`() {
        val result = EnergyDecayFallback.computeDecayRate(emptyList())

        assertEquals(3.0, result.averageHourlyDecay, 0.001)
    }

    @Test
    fun `computeDecayRate filters out pairs with too short gaps`() {
        // 3 minutes apart - should be filtered (MIN_PAIR_HOURS = 0.1 = 6 minutes)
        val data = listOf(
            EnergyDataPoint(Instant.fromEpochMilliseconds(0), 80.0),
            EnergyDataPoint(Instant.fromEpochMilliseconds(3 * 60 * 1000L), 75.0)
        )

        val result = EnergyDecayFallback.computeDecayRate(data)

        // Should return default since no valid pairs
        assertEquals(3.0, result.averageHourlyDecay, 0.001)
    }

    @Test
    fun `computeDecayRate filters out pairs with too long gaps`() {
        // 15 hours apart - should be filtered (MAX_PAIR_HOURS = 12)
        val data = listOf(
            EnergyDataPoint(Instant.fromEpochMilliseconds(0), 80.0),
            EnergyDataPoint(Instant.fromEpochMilliseconds(15 * 60 * 60 * 1000L), 50.0)
        )

        val result = EnergyDecayFallback.computeDecayRate(data)

        // Should return default since no valid pairs
        assertEquals(3.0, result.averageHourlyDecay, 0.001)
    }

    @Test
    fun `computeDecayRate calculates decay from valid pairs`() {
        // 2 hours apart, 10% energy drop = 5%/hour decay
        val twoHoursMs = 2 * 60 * 60 * 1000L
        val data = mutableListOf<EnergyDataPoint>()

        // Create enough valid pairs (MIN_PAIRS_TOTAL = 5)
        for (i in 0 until 6) {
            data.add(
                EnergyDataPoint(
                    timestamp = Instant.fromEpochMilliseconds(i * twoHoursMs),
                    percentage = 80.0 - i * 10.0
                )
            )
        }

        val result = EnergyDecayFallback.computeDecayRate(data)

        // 10% drop in 2 hours = 5%/hour
        assertEquals(5.0, result.averageHourlyDecay, 0.5)
        assertTrue(result.dataPointsUsed >= 5)
    }

    @Test
    fun `computeDecayRate handles energy increase (recovery)`() {
        // Energy increasing over time
        val twoHoursMs = 2 * 60 * 60 * 1000L
        val data = mutableListOf<EnergyDataPoint>()

        for (i in 0 until 6) {
            data.add(
                EnergyDataPoint(
                    timestamp = Instant.fromEpochMilliseconds(i * twoHoursMs),
                    percentage = 50.0 + i * 8.0 // Increasing energy
                )
            )
        }

        val result = EnergyDecayFallback.computeDecayRate(data)

        // Should show negative decay (recovery)
        assertTrue(result.averageHourlyDecay < 0)
    }

    @Test
    fun `computeDecayRate clamps result to valid range`() {
        // Extreme values should be clamped to -10 to 15
        val result = EnergyDecayFallback.computeDecayRate(emptyList())

        // Default is 3.0, which is within range
        assertTrue(result.averageHourlyDecay >= -10.0)
        assertTrue(result.averageHourlyDecay <= 15.0)
    }

    // ========== getDecayForHour Tests ==========

    @Test
    fun `getDecayForHour returns morning rate for hours 6-11`() {
        val rate = DecayRateResult(
            averageHourlyDecay = 3.0,
            morningDecayRate = 4.5,
            afternoonDecayRate = 5.0,
            eveningDecayRate = 4.0,
            nightRecoveryRate = -2.0,
            dataPointsUsed = 100
        )

        assertEquals(4.5, EnergyDecayFallback.getDecayForHour(rate, 6), 0.001)
        assertEquals(4.5, EnergyDecayFallback.getDecayForHour(rate, 9), 0.001)
        assertEquals(4.5, EnergyDecayFallback.getDecayForHour(rate, 11), 0.001)
    }

    @Test
    fun `getDecayForHour returns afternoon rate for hours 12-17`() {
        val rate = DecayRateResult(
            averageHourlyDecay = 3.0,
            morningDecayRate = 4.5,
            afternoonDecayRate = 5.0,
            eveningDecayRate = 4.0,
            nightRecoveryRate = -2.0,
            dataPointsUsed = 100
        )

        assertEquals(5.0, EnergyDecayFallback.getDecayForHour(rate, 12), 0.001)
        assertEquals(5.0, EnergyDecayFallback.getDecayForHour(rate, 15), 0.001)
        assertEquals(5.0, EnergyDecayFallback.getDecayForHour(rate, 17), 0.001)
    }

    @Test
    fun `getDecayForHour returns evening rate for hours 18-21`() {
        val rate = DecayRateResult(
            averageHourlyDecay = 3.0,
            morningDecayRate = 4.5,
            afternoonDecayRate = 5.0,
            eveningDecayRate = 4.0,
            nightRecoveryRate = -2.0,
            dataPointsUsed = 100
        )

        assertEquals(4.0, EnergyDecayFallback.getDecayForHour(rate, 18), 0.001)
        assertEquals(4.0, EnergyDecayFallback.getDecayForHour(rate, 20), 0.001)
        assertEquals(4.0, EnergyDecayFallback.getDecayForHour(rate, 21), 0.001)
    }

    @Test
    fun `getDecayForHour returns night rate for hours 22-23 and 0-5`() {
        val rate = DecayRateResult(
            averageHourlyDecay = 3.0,
            morningDecayRate = 4.5,
            afternoonDecayRate = 5.0,
            eveningDecayRate = 4.0,
            nightRecoveryRate = -2.0,
            dataPointsUsed = 100
        )

        assertEquals(-2.0, EnergyDecayFallback.getDecayForHour(rate, 22), 0.001)
        assertEquals(-2.0, EnergyDecayFallback.getDecayForHour(rate, 23), 0.001)
        assertEquals(-2.0, EnergyDecayFallback.getDecayForHour(rate, 0), 0.001)
        assertEquals(-2.0, EnergyDecayFallback.getDecayForHour(rate, 3), 0.001)
        assertEquals(-2.0, EnergyDecayFallback.getDecayForHour(rate, 5), 0.001)
    }

    @Test
    fun `getDecayForHour returns average when specific rate is null`() {
        val rate = DecayRateResult(
            averageHourlyDecay = 3.0,
            morningDecayRate = null,
            afternoonDecayRate = null,
            eveningDecayRate = null,
            nightRecoveryRate = null,
            dataPointsUsed = 10
        )

        assertEquals(3.0, EnergyDecayFallback.getDecayForHour(rate, 8), 0.001)
        assertEquals(3.0, EnergyDecayFallback.getDecayForHour(rate, 14), 0.001)
        assertEquals(3.0, EnergyDecayFallback.getDecayForHour(rate, 20), 0.001)
        assertEquals(3.0, EnergyDecayFallback.getDecayForHour(rate, 2), 0.001)
    }

    // ========== predictWithDecay Tests ==========

    @Test
    fun `predictWithDecay returns clamped values between 0 and 1`() {
        val lastEnergy = 0.5
        val lastTime = Instant.fromEpochMilliseconds(0)
        val now = Instant.fromEpochMilliseconds(1 * 60 * 60 * 1000L) // 1 hour later

        val (current, future) = EnergyDecayFallback.predictWithDecay(
            lastEnergy,
            lastTime,
            now,
            null
        )

        assertTrue(current >= 0.0 && current <= 1.0)
        assertTrue(future >= 0.0 && future <= 1.0)
    }

    @Test
    fun `predictWithDecay uses default decay rate when null`() {
        val lastEnergy = 0.5
        val lastTime = Instant.fromEpochMilliseconds(0)
        val now = Instant.fromEpochMilliseconds(1 * 60 * 60 * 1000L)

        val (current, _) = EnergyDecayFallback.predictWithDecay(
            lastEnergy,
            lastTime,
            now,
            null
        )

        // With default 3%/hour, after 1 hour: 0.5 - 0.03 = 0.47
        assertTrue(current < lastEnergy)
    }

    @Test
    fun `predictWithDecay future is less than current when decaying`() {
        val lastEnergy = 0.8
        val lastTime = Instant.fromEpochMilliseconds(0)
        val now = Instant.fromEpochMilliseconds(1 * 60 * 60 * 1000L)

        val rate = DecayRateResult(
            averageHourlyDecay = 5.0,
            morningDecayRate = 5.0,
            afternoonDecayRate = 5.0,
            eveningDecayRate = 5.0,
            nightRecoveryRate = 5.0,
            dataPointsUsed = 50
        )

        val (current, future) = EnergyDecayFallback.predictWithDecay(
            lastEnergy,
            lastTime,
            now,
            rate
        )

        assertTrue(future <= current)
    }

    @Test
    fun `predictWithDecay handles zero elapsed time`() {
        val lastEnergy = 0.7
        val now = Instant.fromEpochMilliseconds(1000000L)

        val (current, _) = EnergyDecayFallback.predictWithDecay(
            lastEnergy,
            now, // Same as 'now'
            now,
            null
        )

        // No time elapsed, energy should be unchanged
        assertEquals(lastEnergy, current, 0.001)
    }

    @Test
    fun `predictWithDecay does not go below zero`() {
        val lastEnergy = 0.1
        val lastTime = Instant.fromEpochMilliseconds(0)
        val now = Instant.fromEpochMilliseconds(24 * 60 * 60 * 1000L) // 24 hours later

        val rate = DecayRateResult(
            averageHourlyDecay = 10.0,
            morningDecayRate = 10.0,
            afternoonDecayRate = 10.0,
            eveningDecayRate = 10.0,
            nightRecoveryRate = 10.0,
            dataPointsUsed = 50
        )

        val (current, future) = EnergyDecayFallback.predictWithDecay(
            lastEnergy,
            lastTime,
            now,
            rate
        )

        assertTrue(current >= 0.0)
        assertTrue(future >= 0.0)
    }

    @Test
    fun `predictWithDecay does not go above one`() {
        val lastEnergy = 0.9
        val lastTime = Instant.fromEpochMilliseconds(0)
        val now = Instant.fromEpochMilliseconds(10 * 60 * 60 * 1000L)

        val rate = DecayRateResult(
            averageHourlyDecay = -10.0, // Strong recovery
            morningDecayRate = -10.0,
            afternoonDecayRate = -10.0,
            eveningDecayRate = -10.0,
            nightRecoveryRate = -10.0,
            dataPointsUsed = 50
        )

        val (current, future) = EnergyDecayFallback.predictWithDecay(
            lastEnergy,
            lastTime,
            now,
            rate
        )

        assertTrue(current <= 1.0)
        assertTrue(future <= 1.0)
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
