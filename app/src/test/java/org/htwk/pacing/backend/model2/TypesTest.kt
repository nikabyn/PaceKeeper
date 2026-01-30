package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TypesTest {

    @Test
    fun `HRDataPoint stores timestamp and bpm correctly`() {
        val timestamp = Instant.fromEpochMilliseconds(1000000L)
        val point = HRDataPoint(timestamp, 72.5)

        assertEquals(timestamp, point.timestamp)
        assertEquals(72.5, point.bpm, 0.001)
    }

    @Test
    fun `EnergyDataPoint stores values with default validation`() {
        val timestamp = Instant.fromEpochMilliseconds(2000000L)
        val point = EnergyDataPoint(timestamp, 85.0)

        assertEquals(timestamp, point.timestamp)
        assertEquals(85.0, point.percentage, 0.001)
        assertNull(point.validation)
    }

    @Test
    fun `EnergyDataPoint stores values with custom validation`() {
        val timestamp = Instant.fromEpochMilliseconds(2000000L)
        val point = EnergyDataPoint(timestamp, 85.0, "USER")

        assertEquals("USER", point.validation)
    }

    @Test
    fun `SleepPhase stores start and end correctly`() {
        val start = Instant.fromEpochMilliseconds(1000000L)
        val end = Instant.fromEpochMilliseconds(2000000L)
        val phase = SleepPhase(start, end)

        assertEquals(start, phase.start)
        assertEquals(end, phase.end)
    }

    @Test
    fun `WakeEvent stores timestamp correctly`() {
        val timestamp = Instant.fromEpochMilliseconds(3000000L)
        val event = WakeEvent(timestamp)

        assertEquals(timestamp, event.timestamp)
    }

    @Test
    fun `SleepConfig has correct default values`() {
        val config = SleepConfig()

        assertEquals(62.0, config.sleepHRThreshold, 0.001)
        assertEquals(70.0, config.wakeHRThreshold, 0.001)
        assertEquals(200, config.minSleepMinutes)
        assertEquals(false, config.resetOnWake)
    }

    @Test
    fun `SleepConfig accepts custom values`() {
        val config = SleepConfig(
            sleepHRThreshold = 55.0,
            wakeHRThreshold = 75.0,
            minSleepMinutes = 180,
            resetOnWake = true
        )

        assertEquals(55.0, config.sleepHRThreshold, 0.001)
        assertEquals(75.0, config.wakeHRThreshold, 0.001)
        assertEquals(180, config.minSleepMinutes)
        assertEquals(true, config.resetOnWake)
    }

    @Test
    fun `SleepCycle stores all values correctly`() {
        val start = Instant.fromEpochMilliseconds(1000000L)
        val end = Instant.fromEpochMilliseconds(2000000L)
        val cycle = SleepCycle(start, end, "2024-01-15")

        assertEquals(start, cycle.cycleStart)
        assertEquals(end, cycle.cycleEnd)
        assertEquals("2024-01-15", cycle.label)
    }

    @Test
    fun `OptimizationResult stores all values correctly`() {
        val result = OptimizationResult(
            hrLow = 58.0,
            hrHigh = 85.0,
            drainFactor = 1.5,
            recoveryFactor = 0.9,
            loss = 25.5,
            energyOffset = -5.0
        )

        assertEquals(58.0, result.hrLow, 0.001)
        assertEquals(85.0, result.hrHigh, 0.001)
        assertEquals(1.5, result.drainFactor, 0.001)
        assertEquals(0.9, result.recoveryFactor, 0.001)
        assertEquals(25.5, result.loss, 0.001)
        assertEquals(-5.0, result.energyOffset, 0.001)
    }

    @Test
    fun `OptimizationResult has default energyOffset of zero`() {
        val result = OptimizationResult(60.0, 100.0, 1.0, 1.0, 10.0)

        assertEquals(0.0, result.energyOffset, 0.001)
    }

    @Test
    fun `DayFitResult stores all values correctly`() {
        val result = DayFitResult(
            hrLow = 59.0,
            hrHigh = 95.0,
            drainFactor = 1.2,
            recoveryFactor = 0.8,
            loss = 15.0,
            energyOffset = -3.0,
            date = "2024-01-15",
            dataPoints = 48
        )

        assertEquals(59.0, result.hrLow, 0.001)
        assertEquals(95.0, result.hrHigh, 0.001)
        assertEquals(1.2, result.drainFactor, 0.001)
        assertEquals(0.8, result.recoveryFactor, 0.001)
        assertEquals(15.0, result.loss, 0.001)
        assertEquals(-3.0, result.energyOffset, 0.001)
        assertEquals("2024-01-15", result.date)
        assertEquals(48, result.dataPoints)
    }

    @Test
    fun `FitRange enum has correct values`() {
        assertEquals(3, FitRange.entries.size)
        assertEquals(FitRange.ALL, FitRange.valueOf("ALL"))
        assertEquals(FitRange.MONTH, FitRange.valueOf("MONTH"))
        assertEquals(FitRange.WEEK, FitRange.valueOf("WEEK"))
    }

    @Test
    fun `AutoFitResult stores all values correctly`() {
        val optResult = OptimizationResult(60.0, 100.0, 1.0, 1.0, 10.0)
        val dayResults = listOf(
            DayFitResult(59.0, 95.0, 1.2, 0.8, 15.0, 0.0, "2024-01-15", 48)
        )

        val autoFit = AutoFitResult(
            result = optResult,
            dayResults = dayResults,
            usedDays = 5,
            totalDays = 7
        )

        assertEquals(optResult, autoFit.result)
        assertEquals(dayResults, autoFit.dayResults)
        assertEquals(5, autoFit.usedDays)
        assertEquals(7, autoFit.totalDays)
    }

    @Test
    fun `EnergyConfig has correct default values`() {
        val config = EnergyConfig()

        assertEquals(59.5, config.hrLow, 0.001)
        assertEquals(83.2, config.hrHigh, 0.001)
        assertEquals(120, config.timeOffsetMinutes)
        assertEquals(0.8, config.recoveryFactor, 0.001)
        assertEquals(1.79, config.drainFactor, 0.001)
        assertEquals(15, config.aggregationMinutes)
        assertEquals(-6.9, config.energyOffset, 0.001)
    }

    @Test
    fun `HRVPoint stores timestamp and rmssd correctly`() {
        val timestamp = Instant.fromEpochMilliseconds(1000000L)
        val point = HRVPoint(timestamp, 45.5)

        assertEquals(timestamp, point.timestamp)
        assertEquals(45.5, point.rmssd, 0.001)
    }

    @Test
    fun `HRVDrainConfig has correct default values`() {
        val config = HRVDrainConfig()

        assertEquals(60, config.windowSeconds)
        assertEquals(24, config.baselineWindowHours)
        assertEquals(1.5, config.lowHRVMultiplier, 0.001)
        assertEquals(1.0, config.normalHRVMultiplier, 0.001)
        assertEquals(0.5, config.highHRVMultiplier, 0.001)
        assertEquals(0.7, config.lowThreshold, 0.001)
        assertEquals(1.3, config.highThreshold, 0.001)
    }

    @Test
    fun `CycleData stores all values correctly`() {
        val start = Instant.fromEpochMilliseconds(1000000L)
        val end = Instant.fromEpochMilliseconds(2000000L)
        val validatedPoints = listOf(
            EnergyDataPoint(Instant.fromEpochMilliseconds(1500000L), 70.0)
        )
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(1500000L), 65.0)
        )

        val cycleData = CycleData(
            label = "2024-01-15",
            cycleStart = start,
            cycleEnd = end,
            validatedPoints = validatedPoints,
            hrData = hrData,
            startEnergy = 80.0
        )

        assertEquals("2024-01-15", cycleData.label)
        assertEquals(start, cycleData.cycleStart)
        assertEquals(end, cycleData.cycleEnd)
        assertEquals(validatedPoints, cycleData.validatedPoints)
        assertEquals(hrData, cycleData.hrData)
        assertEquals(80.0, cycleData.startEnergy, 0.001)
    }

    @Test
    fun `EnergyResult stores timestamp and energy correctly`() {
        val timestamp = Instant.fromEpochMilliseconds(1000000L)
        val result = EnergyResult(timestamp, 75.0)

        assertEquals(timestamp, result.timestamp)
        assertEquals(75.0, result.energy, 0.001)
    }

    @Test
    fun `EnergyResultWithHRV stores all values correctly`() {
        val timestamp = Instant.fromEpochMilliseconds(1000000L)
        val result = EnergyResultWithHRV(timestamp, 75.0, 1.2)

        assertEquals(timestamp, result.timestamp)
        assertEquals(75.0, result.energy, 0.001)
        assertEquals(1.2, result.hrvMultiplier, 0.001)
    }

    @Test
    fun `DecayRateResult stores all values correctly`() {
        val result = DecayRateResult(
            averageHourlyDecay = 3.5,
            morningDecayRate = 4.0,
            afternoonDecayRate = 5.0,
            eveningDecayRate = 4.5,
            nightRecoveryRate = -2.0,
            dataPointsUsed = 100
        )

        assertEquals(3.5, result.averageHourlyDecay, 0.001)
        assertEquals(4.0, result.morningDecayRate!!, 0.001)
        assertEquals(5.0, result.afternoonDecayRate!!, 0.001)
        assertEquals(4.5, result.eveningDecayRate!!, 0.001)
        assertEquals(-2.0, result.nightRecoveryRate!!, 0.001)
        assertEquals(100, result.dataPointsUsed)
    }

    @Test
    fun `DecayRateResult allows null time-of-day rates`() {
        val result = DecayRateResult(
            averageHourlyDecay = 3.0,
            morningDecayRate = null,
            afternoonDecayRate = null,
            eveningDecayRate = null,
            nightRecoveryRate = null,
            dataPointsUsed = 10
        )

        assertNull(result.morningDecayRate)
        assertNull(result.afternoonDecayRate)
        assertNull(result.eveningDecayRate)
        assertNull(result.nightRecoveryRate)
    }
}
