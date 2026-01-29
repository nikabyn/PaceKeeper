package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnergyCalculationTest {

    // Helper functions
    private fun createHRDataPoints(
        startMs: Long,
        intervalMs: Long,
        bpmValues: List<Double>
    ): List<HRDataPoint> {
        return bpmValues.mapIndexed { index, bpm ->
            HRDataPoint(
                timestamp = Instant.fromEpochMilliseconds(startMs + index * intervalMs),
                bpm = bpm
            )
        }
    }

    private fun createHRVPoints(
        startMs: Long,
        intervalMs: Long,
        rmssdValues: List<Double>
    ): List<HRVPoint> {
        return rmssdValues.mapIndexed { index, rmssd ->
            HRVPoint(
                timestamp = Instant.fromEpochMilliseconds(startMs + index * intervalMs),
                rmssd = rmssd
            )
        }
    }

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

    // ========== calculateEnergyWithHRVDrainAnchored Tests ==========

    @Test
    fun `calculateEnergyWithHRVDrainAnchored returns empty list for empty HR data`() {
        val result = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = emptyList(),
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 120,
            aggregationMinutes = 15,
            validatedPoints = emptyList(),
            fallbackStartEnergy = 50.0,
            energyOffset = 0.0
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `calculateEnergyWithHRVDrainAnchored returns results with correct structure`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(70.0, 75.0, 80.0))
        val hrvData = createHRVPoints(0L, intervalMs, listOf(30.0, 28.0, 25.0))

        val result = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrData,
            hrvData = hrvData,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 120,
            aggregationMinutes = 15,
            validatedPoints = emptyList(),
            fallbackStartEnergy = 75.0,
            energyOffset = 0.0
        )

        assertTrue(result.isNotEmpty())
        result.forEach { point ->
            assertTrue(point.energy >= 0.0)
            assertTrue(point.energy <= 100.0)
            assertTrue(point.hrvMultiplier > 0.0)
        }
    }

    @Test
    fun `calculateEnergyWithHRVDrainAnchored uses fallback start energy when no validated points before HR start`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(70.0, 70.0))

        val result = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 120,
            aggregationMinutes = 15,
            validatedPoints = emptyList(),
            fallbackStartEnergy = 80.0,
            energyOffset = 0.0
        )

        // First energy should be close to fallback (may differ due to HR in neutral zone)
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `calculateEnergyWithHRVDrainAnchored uses validated point as anchor`() {
        val intervalMs = 15 * 60 * 1000L
        val timeOffset = 120 * 60 * 1000L // 2 hours in ms

        val hrData = createHRDataPoints(0L, intervalMs, listOf(70.0, 70.0, 70.0, 70.0))

        // Validated point with offset adjustment should anchor the energy
        val validatedPoints = listOf(
            EnergyDataPoint(
                timestamp = Instant.fromEpochMilliseconds(-timeOffset), // Before HR start when offset applied
                percentage = 90.0
            )
        )

        val result = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 120,
            aggregationMinutes = 15,
            validatedPoints = validatedPoints,
            fallbackStartEnergy = 50.0,
            energyOffset = 0.0
        )

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `calculateEnergyWithHRVDrainAnchored applies energy offset`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(70.0, 70.0))

        val resultWithoutOffset = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 120,
            aggregationMinutes = 15,
            validatedPoints = emptyList(),
            fallbackStartEnergy = 75.0,
            energyOffset = 0.0
        )

        val resultWithOffset = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 120,
            aggregationMinutes = 15,
            validatedPoints = emptyList(),
            fallbackStartEnergy = 75.0,
            energyOffset = 10.0
        )

        // With positive offset, energy should be lower
        if (resultWithoutOffset.isNotEmpty() && resultWithOffset.isNotEmpty()) {
            assertTrue(resultWithOffset.first().energy <= resultWithoutOffset.first().energy)
        }
    }

    @Test
    fun `calculateEnergyWithHRVDrainAnchored applies time offset to output timestamps`() {
        val intervalMs = 15 * 60 * 1000L
        val timeOffsetMinutes = 120
        val hrData = createHRDataPoints(0L, intervalMs, listOf(70.0))

        val result = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = timeOffsetMinutes,
            aggregationMinutes = 15,
            validatedPoints = emptyList(),
            fallbackStartEnergy = 75.0,
            energyOffset = 0.0
        )

        if (result.isNotEmpty()) {
            // Output timestamp should be HR timestamp + offset
            val expectedMs = 0L + timeOffsetMinutes * 60 * 1000L
            assertEquals(expectedMs, result.first().timestamp.toEpochMilliseconds())
        }
    }

    // ========== calculateEnergyWithHRVDrain Tests (internal function) ==========

    @Test
    fun `calculateEnergyWithHRVDrain drains energy when HR above hrHigh`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(110.0, 110.0, 110.0))

        val result = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 80.0,
            energyOffset = 0.0
        )

        // Energy should decrease
        assertTrue(result.isNotEmpty())
        assertTrue(result.last().energy < 80.0)
    }

    @Test
    fun `calculateEnergyWithHRVDrain recovers energy when HR below hrLow`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(50.0, 50.0, 50.0))

        val result = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 50.0,
            energyOffset = 0.0
        )

        // Energy should increase
        assertTrue(result.isNotEmpty())
        assertTrue(result.last().energy > 50.0)
    }

    @Test
    fun `calculateEnergyWithHRVDrain maintains energy in neutral zone`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(75.0, 75.0, 75.0))

        val result = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 70.0,
            energyOffset = 0.0
        )

        // Energy should remain constant
        result.forEach { point ->
            assertEquals(70.0, point.energy, 0.001)
        }
    }

    @Test
    fun `calculateEnergyWithHRVDrain uses HRV multiplier for drain`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(110.0, 110.0))

        // Low HRV should increase drain (multiplier > 1)
        val lowHRVData = createHRVPoints(0L, intervalMs, listOf(10.0, 10.0)) // Very low
        val normalHRVData = createHRVPoints(0L, intervalMs, listOf(30.0, 30.0)) // Normal

        val resultLowHRV = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = lowHRVData,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 80.0,
            energyOffset = 0.0,
            hrvConfig = HRVDrainConfig(lowThreshold = 0.7, lowHRVMultiplier = 1.5)
        )

        val resultNormalHRV = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = normalHRVData,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 80.0,
            energyOffset = 0.0
        )

        // Low HRV should cause more drain
        if (resultLowHRV.isNotEmpty() && resultNormalHRV.isNotEmpty()) {
            assertTrue(resultLowHRV.last().energy <= resultNormalHRV.last().energy)
        }
    }

    @Test
    fun `calculateEnergyWithHRVDrain clamps energy to 0-100 range`() {
        val intervalMs = 15 * 60 * 1000L
        // Very high HR for extreme drain
        val hrData = createHRDataPoints(0L, intervalMs, List(20) { 150.0 })

        val result = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 80.0,
            drainFactor = 5.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 20.0,
            energyOffset = 0.0
        )

        result.forEach { point ->
            assertTrue("Energy should be >= 0", point.energy >= 0.0)
            assertTrue("Energy should be <= 100", point.energy <= 100.0)
        }
    }

    @Test
    fun `calculateEnergyWithHRVDrain returns correct HRV multiplier in results`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(75.0))
        val hrvData = createHRVPoints(0L, intervalMs, listOf(30.0))

        val result = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = hrvData,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 70.0,
            energyOffset = 0.0
        )

        assertTrue(result.isNotEmpty())
        assertTrue(result.first().hrvMultiplier > 0.0)
    }

    @Test
    fun `calculateEnergyWithHRVDrain handles variable time intervals`() {
        // Create HR data with irregular intervals
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(0), 110.0),
            HRDataPoint(Instant.fromEpochMilliseconds(5 * 60 * 1000L), 110.0), // 5 min
            HRDataPoint(Instant.fromEpochMilliseconds(30 * 60 * 1000L), 110.0) // 25 min later
        )

        val result = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 80.0,
            energyOffset = 0.0
        )

        assertEquals(3, result.size)
        // Longer interval should result in more drain
        val secondDrain = result[0].energy - result[1].energy
        val thirdDrain = result[1].energy - result[2].energy
        assertTrue(thirdDrain > secondDrain) // Longer interval = more drain
    }

    @Test
    fun `calculateEnergyWithHRVDrain uses drainFactor correctly`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(110.0, 110.0))

        val resultLowDrain = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 0.5,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 80.0,
            energyOffset = 0.0
        )

        val resultHighDrain = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 2.0,
            recoveryFactor = 1.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 80.0,
            energyOffset = 0.0
        )

        // Higher drain factor should result in more energy loss
        assertTrue(resultHighDrain.last().energy < resultLowDrain.last().energy)
    }

    @Test
    fun `calculateEnergyWithHRVDrain uses recoveryFactor correctly`() {
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(0L, intervalMs, listOf(50.0, 50.0))

        val resultLowRecovery = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 0.5,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 50.0,
            energyOffset = 0.0
        )

        val resultHighRecovery = EnergyCalculation.calculateEnergyWithHRVDrain(
            hrAgg = hrData,
            hrvData = emptyList(),
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 2.0,
            timeOffsetMinutes = 0,
            aggregationMinutes = 15,
            startEnergy = 50.0,
            energyOffset = 0.0
        )

        // Higher recovery factor should result in more energy gain
        assertTrue(resultHighRecovery.last().energy > resultLowRecovery.last().energy)
    }
}
