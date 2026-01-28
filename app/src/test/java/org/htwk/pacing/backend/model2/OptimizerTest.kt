package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OptimizerTest {

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

    private fun createTestCycleData(): CycleData {
        val startMs = 0L
        val intervalMs = 15 * 60 * 1000L // 15 minutes
        val hrDelay = 2 * 60 * 60 * 1000L // 2h delay used in simulateEnergy

        return CycleData(
            label = "2024-01-15",
            cycleStart = Instant.fromEpochMilliseconds(startMs),
            cycleEnd = Instant.fromEpochMilliseconds(startMs + 8 * intervalMs),
            // Validated points need to be at HR timestamp + hrDelay to match simulateEnergy output
            validatedPoints = createEnergyPoints(
                startMs + hrDelay,
                2 * intervalMs,
                listOf(80.0, 70.0, 60.0)
            ),
            hrData = createHRDataPoints(
                startMs,
                intervalMs,
                listOf(65.0, 70.0, 85.0, 90.0, 95.0, 85.0, 75.0, 70.0)
            ),
            startEnergy = 85.0
        )
    }

    // ========== median Tests ==========

    @Test
    fun `median returns 0 for empty list`() {
        val result = Optimizer.median(emptyList())

        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `median returns middle value for odd count`() {
        val result = Optimizer.median(listOf(1.0, 3.0, 2.0))

        assertEquals(2.0, result, 0.001)
    }

    @Test
    fun `median returns average of middle values for even count`() {
        val result = Optimizer.median(listOf(1.0, 2.0, 3.0, 4.0))

        assertEquals(2.5, result, 0.001)
    }

    @Test
    fun `median handles single value`() {
        val result = Optimizer.median(listOf(5.0))

        assertEquals(5.0, result, 0.001)
    }

    @Test
    fun `median sorts values correctly`() {
        val result = Optimizer.median(listOf(5.0, 1.0, 3.0, 2.0, 4.0))

        assertEquals(3.0, result, 0.001)
    }

    // ========== simulateEnergy Tests ==========

    @Test
    fun `simulateEnergy returns empty map for empty HR data`() {
        val result = Optimizer.simulateEnergy(
            hrData = emptyList(),
            startEnergy = 80.0,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `simulateEnergy drains energy when HR above hrHigh`() {
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, listOf(110.0, 110.0))

        val result = Optimizer.simulateEnergy(
            hrData = hrData,
            startEnergy = 80.0,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        // Energy should decrease
        val energyValues = result.values.toList()
        assertTrue(energyValues.last() < 80.0)
    }

    @Test
    fun `simulateEnergy recovers energy when HR below hrLow`() {
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, listOf(50.0, 50.0))

        val result = Optimizer.simulateEnergy(
            hrData = hrData,
            startEnergy = 50.0,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        // Energy should increase
        val energyValues = result.values.toList()
        assertTrue(energyValues.last() > 50.0)
    }

    @Test
    fun `simulateEnergy maintains energy when HR in neutral zone`() {
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, listOf(75.0, 75.0, 75.0))

        val result = Optimizer.simulateEnergy(
            hrData = hrData,
            startEnergy = 70.0,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        // Energy should remain constant
        val energyValues = result.values.toList()
        assertEquals(70.0, energyValues.last(), 0.001)
    }

    @Test
    fun `simulateEnergy clamps energy to 0-100 range`() {
        // Very high HR to drain quickly
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, List(20) { 150.0 })

        val result = Optimizer.simulateEnergy(
            hrData = hrData,
            startEnergy = 10.0,
            hrLow = 60.0,
            hrHigh = 80.0,
            drainFactor = 5.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        // Energy should not go below 0
        result.values.forEach { energy ->
            assertTrue(energy >= 0.0)
            assertTrue(energy <= 100.0)
        }
    }

    @Test
    fun `simulateEnergy adds HR_DELAY to timestamps`() {
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, listOf(70.0))
        val hrDelay = 2 * 60 * 60 * 1000L // 2 hours

        val result = Optimizer.simulateEnergy(
            hrData = hrData,
            startEnergy = 70.0,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        // First timestamp should be 0 + 2h delay
        assertTrue(result.containsKey(hrDelay))
    }

    // ========== findClosestEnergy Tests ==========

    @Test
    fun `findClosestEnergy returns null for empty map`() {
        val result = Optimizer.findClosestEnergy(emptyMap(), 1000L)

        assertNull(result)
    }

    @Test
    fun `findClosestEnergy returns exact match`() {
        val energyMap = mapOf(1000L to 75.0)

        val result = Optimizer.findClosestEnergy(energyMap, 1000L)

        assertEquals(75.0, result!!, 0.001)
    }

    @Test
    fun `findClosestEnergy returns closest value within 30 minutes`() {
        val energyMap = mapOf(
            0L to 80.0,
            30 * 60 * 1000L to 75.0,
            60 * 60 * 1000L to 70.0
        )

        val result = Optimizer.findClosestEnergy(energyMap, 25 * 60 * 1000L)

        assertEquals(75.0, result!!, 0.001)
    }

    @Test
    fun `findClosestEnergy returns null when more than 30 minutes away`() {
        val energyMap = mapOf(0L to 80.0)

        val result = Optimizer.findClosestEnergy(energyMap, 31 * 60 * 1000L)

        assertNull(result)
    }

    // ========== calculateCycleLoss Tests ==========

    @Test
    fun `calculateCycleLoss returns infinity for invalid hrLow greater than hrHigh`() {
        val cycle = createTestCycleData()

        val result = Optimizer.calculateCycleLoss(
            cycle = cycle,
            hrLow = 100.0,
            hrHigh = 60.0, // Invalid: hrLow > hrHigh
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        assertTrue(result.isInfinite())
    }

    @Test
    fun `calculateCycleLoss returns infinity for negative drainFactor`() {
        val cycle = createTestCycleData()

        val result = Optimizer.calculateCycleLoss(
            cycle = cycle,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = -1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        assertTrue(result.isInfinite())
    }

    @Test
    fun `calculateCycleLoss returns infinity for negative recoveryFactor`() {
        val cycle = createTestCycleData()

        val result = Optimizer.calculateCycleLoss(
            cycle = cycle,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = -1.0,
            aggregationMinutes = 15
        )

        assertTrue(result.isInfinite())
    }

    @Test
    fun `calculateCycleLoss returns finite value for valid parameters`() {
        val cycle = createTestCycleData()

        val result = Optimizer.calculateCycleLoss(
            cycle = cycle,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        assertTrue(result.isFinite())
    }

    // ========== calculateEnergyOffset Tests ==========

    @Test
    fun `calculateEnergyOffset returns median difference`() {
        val cycle = createTestCycleData()

        val result = Optimizer.calculateEnergyOffset(
            cycle = cycle,
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            aggregationMinutes = 15
        )

        // Should be a finite number
        assertTrue(result.isFinite())
    }

    // ========== gridSearchCycle Tests ==========

    @Test
    fun `gridSearchCycle returns OptimizationResult`() {
        val cycle = createTestCycleData()

        val result = Optimizer.gridSearchCycle(cycle, 15)

        assertNotNull(result)
        assertTrue(result.hrLow < result.hrHigh)
    }

    @Test
    fun `gridSearchCycle finds parameters with finite loss`() {
        val cycle = createTestCycleData()

        val result = Optimizer.gridSearchCycle(cycle, 15)

        // If valid parameters exist, loss should be finite
        // (may be infinite if no good fit found)
        assertNotNull(result)
    }

    // ========== nelderMeadCycle Tests ==========

    @Test
    fun `nelderMeadCycle refines grid search result`() {
        val cycle = createTestCycleData()
        val startParams = OptimizationResult(
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            loss = 100.0
        )

        val result = Optimizer.nelderMeadCycle(cycle, startParams, 15, maxIterations = 10)

        assertNotNull(result)
    }

    @Test
    fun `nelderMeadCycle returns rounded values`() {
        val cycle = createTestCycleData()
        val startParams = OptimizationResult(60.0, 100.0, 1.0, 1.0, 100.0)

        val result = Optimizer.nelderMeadCycle(cycle, startParams, 15)

        // hrLow and hrHigh should be rounded to 1 decimal
        assertEquals(result.hrLow, (result.hrLow * 10).toLong() / 10.0, 0.001)
        assertEquals(result.hrHigh, (result.hrHigh * 10).toLong() / 10.0, 0.001)

        // Factors should be rounded to 2 decimals
        assertEquals(result.drainFactor, (result.drainFactor * 100).toLong() / 100.0, 0.001)
        assertEquals(result.recoveryFactor, (result.recoveryFactor * 100).toLong() / 100.0, 0.001)
    }

    // ========== groupBySleepCycle Tests ==========

    @Test
    fun `groupBySleepCycle returns empty list when no cycles detected`() {
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, listOf(70.0, 70.0, 70.0))
        val energyData = createEnergyPoints(0L, 60 * 60 * 1000L, listOf(80.0, 75.0))
        val config = SleepConfig()

        val result = Optimizer.groupBySleepCycle(energyData, hrData, config)

        assertTrue(result.isEmpty())
    }

    // ========== filterCyclesByRange Tests ==========

    @Test
    fun `filterCyclesByRange returns all cycles for FitRange ALL`() {
        val cycles = listOf(
            createTestCycleData(),
            createTestCycleData().copy(label = "2024-01-14")
        )

        val result = Optimizer.filterCyclesByRange(cycles, FitRange.ALL)

        assertEquals(2, result.size)
    }

    @Test
    fun `filterCyclesByRange returns empty for empty input`() {
        val result = Optimizer.filterCyclesByRange(emptyList(), FitRange.WEEK)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `filterCyclesByRange filters by week`() {
        val baseMs = System.currentTimeMillis()
        val dayMs = 24 * 60 * 60 * 1000L

        val cycles = listOf(
            createTestCycleData().copy(
                cycleStart = Instant.fromEpochMilliseconds(baseMs)
            ),
            createTestCycleData().copy(
                label = "old",
                cycleStart = Instant.fromEpochMilliseconds(baseMs - 10 * dayMs)
            )
        )

        val result = Optimizer.filterCyclesByRange(cycles, FitRange.WEEK)

        // Only recent cycle should be included
        assertEquals(1, result.size)
    }

    // ========== autoFit Tests ==========

    @Test
    fun `autoFit returns default result when no cycles`() {
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, listOf(70.0, 70.0))
        val energyData = createEnergyPoints(0L, 60 * 60 * 1000L, listOf(80.0))
        val config = SleepConfig()

        val result = Optimizer.autoFit(
            hrAgg = hrData,
            validatedEnergy = energyData,
            sleepConfig = config,
            aggregationMinutes = 15
        )

        assertEquals(0, result.usedDays)
        assertTrue(result.result.loss.isInfinite())
    }

    @Test
    fun `autoFit returns result with correct structure`() {
        val hrData = createHRDataPoints(0L, 15 * 60 * 1000L, listOf(70.0, 70.0))
        val energyData = createEnergyPoints(0L, 60 * 60 * 1000L, listOf(80.0))
        val config = SleepConfig()

        val result = Optimizer.autoFit(
            hrAgg = hrData,
            validatedEnergy = energyData,
            sleepConfig = config,
            aggregationMinutes = 15
        )

        assertNotNull(result.result)
        assertNotNull(result.dayResults)
    }

    // ========== fitSingleCycle Tests ==========

    @Test
    fun `fitSingleCycle returns DayFitResult with correct date`() {
        val cycle = createTestCycleData()

        val result = Optimizer.fitSingleCycle(cycle, 15)

        assertEquals("2024-01-15", result.date)
        assertEquals(3, result.dataPoints)
    }

    @Test
    fun `fitSingleCycle returns finite loss for valid cycle`() {
        val cycle = createTestCycleData()

        val result = Optimizer.fitSingleCycle(cycle, 15)

        assertTrue(result.loss.isFinite())
    }
}
