package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant

/**
 * Data types for Model 2 energy prediction.
 */

data class HRDataPoint(
    val timestamp: Instant,
    val bpm: Double
)

data class EnergyDataPoint(
    val timestamp: Instant,
    val percentage: Double,
    val validation: String? = null
)

data class SleepPhase(
    val start: Instant,
    val end: Instant
)

data class WakeEvent(
    val timestamp: Instant
)

data class SleepConfig(
    val sleepHRThreshold: Double = 62.0,
    val wakeHRThreshold: Double = 70.0,
    val minSleepMinutes: Int = 200,
    val resetOnWake: Boolean = false
)

data class SleepCycle(
    val cycleStart: Instant,
    val cycleEnd: Instant,
    val label: String
)

data class OptimizationResult(
    val hrLow: Double,
    val hrHigh: Double,
    val drainFactor: Double,
    val recoveryFactor: Double,
    val loss: Double,
    val energyOffset: Double = 0.0
)

data class DayFitResult(
    val hrLow: Double,
    val hrHigh: Double,
    val drainFactor: Double,
    val recoveryFactor: Double,
    val loss: Double,
    val energyOffset: Double = 0.0,
    val date: String,
    val dataPoints: Int
)

enum class FitRange {
    ALL, MONTH, WEEK
}

enum class AggregationMethod {
    MEDIAN, IQR
}

data class AutoFitResult(
    val result: OptimizationResult,
    val dayResults: List<DayFitResult>,
    val usedDays: Int,
    val totalDays: Int
)

data class EnergyConfig(
    val hrLow: Double = 59.5,
    val hrHigh: Double = 83.2,
    val timeOffsetMinutes: Int = 120,
    val recoveryFactor: Double = 0.8,
    val drainFactor: Double = 1.79,
    val aggregationMinutes: Int = 15,
    val energyOffset: Double = -6.9
)

data class HRVPoint(
    val timestamp: Instant,
    val rmssd: Double
)

data class HRVDrainConfig(
    val windowSeconds: Int = 60,
    val baselineWindowHours: Int = 24,
    val lowHRVMultiplier: Double = 1.5,
    val normalHRVMultiplier: Double = 1.0,
    val highHRVMultiplier: Double = 0.5,
    val lowThreshold: Double = 0.7,
    val highThreshold: Double = 1.3
)

data class CycleData(
    val label: String,
    val cycleStart: Instant,
    val cycleEnd: Instant,
    val validatedPoints: List<EnergyDataPoint>,
    val hrData: List<HRDataPoint>,
    val startEnergy: Double
)

data class EnergyResult(
    val timestamp: Instant,
    val energy: Double
)

data class EnergyResultWithHRV(
    val timestamp: Instant,
    val energy: Double,
    val hrvMultiplier: Double
)

data class DecayRateResult(
    val averageHourlyDecay: Double,       // median %/h drain (if positive = drain)
    val morningDecayRate: Double?,         // 06:00-12:00
    val afternoonDecayRate: Double?,       // 12:00-18:00
    val eveningDecayRate: Double?,         // 18:00-22:00
    val nightRecoveryRate: Double?,        // 22:00-06:00 (if negative = recovery)
    val dataPointsUsed: Int
)
