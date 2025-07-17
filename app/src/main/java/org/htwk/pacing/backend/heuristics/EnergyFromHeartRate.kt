package org.htwk.pacing.backend.heuristics

import kotlin.math.max
import kotlin.math.min

object EnergyFromHeartRateCalculator {
    // see https://www.millionsmissing.de/2024/10/08/pacing-bei-me-cfs-herzfrequenz%C3%BCberwachung-zur-vermeidung-von-pem-und-flare-ups/

    const val HEART_RATE_LIMIT = 90.0
    const val ACTIVITY_RECOVERY_RATIO = 8.0
    //const val HR_AT_REST = 75
    //const val HR_PENALTY_START = HR_AT_REST

    fun nextEnergyLevel10MinSimple(energy: Double, hr: Double): Double {
        val threshold: Double = HEART_RATE_LIMIT              // personal threshold (bpm)
        val depletionRate: Double = 20.0 / (60.0 * threshold)  // depletion factor
        val recoveryRate: Double = 2.0 / (60.0 * 10.0)        // per hour
        val deltaT = 10.0 / 60.0              // timestep size in hours

        val baseRate = 1.0 / 36.0

        //val load = max(0.0, hr - threshold)   // excess load from being over threshold
        var load = (threshold - hr) / 20.0
        if (load < 0.0) load *= ACTIVITY_RECOVERY_RATIO
        val nextEnergy =
            (energy + baseRate * load * deltaT).coerceIn(
                0.0,
                1.0
            )
        return nextEnergy
    }

    //TODO: placeholder, to be replaced by scientific model trough ui feature ticket #21
    fun energyLevelFromHeartRate(heartRate: Double /*liste von symptomen*/): Double {
        val heartRateClipped = max(50.0, min(heartRate, 150.0)) - 50.0
        return (100.0 - heartRateClipped) / 100.0
    }
}