package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant

/**
 * Data types for Model 2 energy prediction.
 * Translated from TypeScript optimizer.ts and hrvDrain.ts
 */

// ============== Basic HR Data Point ==============

data class HRDataPoint(
    val timestamp: Instant,
    val bpm: Double
)

// ============== Energy Data Point ==============

data class EnergyDataPoint(
    val timestamp: Instant,
    val percentage: Double,
    val validation: String? = null
)

// ============== Sleep Detection Types ==============

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

// ============== Optimization Types ==============

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

// ============== Energy Configuration ==============

data class EnergyConfig(
    val hrLow: Double = 60.0,
    val hrHigh: Double = 75.0,
    val timeOffsetMinutes: Int = 120,
    val recoveryFactor: Double = 8.6,
    val drainFactor: Double = 0.4,
    val aggregationMinutes: Int = 15,
    val energyOffset: Double = 0.0
)

// ============== HRV Types ==============

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

// ============== Cycle Data for Optimization ==============

data class CycleData(
    val label: String,
    val cycleStart: Instant,
    val cycleEnd: Instant,
    val validatedPoints: List<EnergyDataPoint>,
    val hrData: List<HRDataPoint>,
    val startEnergy: Double
)

// ============== Energy Result ==============

data class EnergyResult(
    val timestamp: Instant,
    val energy: Double
)

data class EnergyResultWithHRV(
    val timestamp: Instant,
    val energy: Double,
    val hrvMultiplier: Double
)

// ============== Decay Fallback Types ==============

data class DecayRateResult(
    val averageHourlyDecay: Double,       // Durchschnittlicher %/h Verfall (0-100 Skala), positiv = Verfall
    val morningDecayRate: Double?,         // 06:00-12:00
    val afternoonDecayRate: Double?,       // 12:00-18:00
    val eveningDecayRate: Double?,         // 18:00-22:00
    val nightRecoveryRate: Double?,        // 22:00-06:00 (kann negativ sein = Erholung)
    val dataPointsUsed: Int
)
