package org.htwk.pacing.backend.heuristics

import kotlin.math.max
import kotlin.math.min

object EnergyFromHeartRateCalculator {
    // see https://www.millionsmissing.de/2024/10/08/pacing-bei-me-cfs-herzfrequenz%C3%BCberwachung-zur-vermeidung-von-pem-und-flare-ups/

    const val HEART_RATE_LIMIT = 90.0
    //const val HR_AT_REST = 75
    //const val HR_PENALTY_START = HR_AT_REST

    /** feed a *predicted* HR sample (10‑min resolution) */
    fun nextEnergyLevelMinutely(currentEnergy: Double, currentHeartRate: Double): Double {
        val threshold: Double = HEART_RATE_LIMIT              // personal threshold (bpm)
        val depletionRate: Double = 1.0 / (60.0 * threshold)  // depletion factor
        val recoveryRate: Double = 0.05        // per hour
        val deltaT = 10.0 / 60.0              // timestep size in hours

        val load = max(0.0, currentHeartRate - threshold)   // excess load from being over threshold
        val nextEnergy =
            (currentEnergy + recoveryRate * deltaT - depletionRate * load * deltaT).coerceIn(
                0.0,
                1.0
            )
        return nextEnergy
    }

    fun heartRate10MinToEnergy10min(
        startingEnergy: Double,
        heartRate10Min: FloatArray
    ): FloatArray {
        val energyLevels = FloatArray(heartRate10Min.size)
        var currentEnergy = startingEnergy.toDouble()

        for (i in heartRate10Min.indices) {
            currentEnergy = nextEnergyLevelMinutely(currentEnergy, heartRate10Min[i].toDouble())
            energyLevels[i] = currentEnergy.toFloat()
        }
        return energyLevels
    }

    //TODO: placeholder, to be replaced by scientific model trough ui feature ticket #21
    fun energyLevelFromHeartRate(heartRate: Double /*liste von symptomen*/): Double {
        val heartRateClipped = max(50.0, min(heartRate, 150.0)) - 50.0
        return (100.0 - heartRateClipped) / 100.0
    }
}