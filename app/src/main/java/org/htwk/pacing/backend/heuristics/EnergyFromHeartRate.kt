package org.htwk.pacing.backend.heuristics

import kotlin.math.max
import kotlin.math.min

object EnergyFromHeartRateCalculator {
    // see https://www.millionsmissing.de/2024/10/08/pacing-bei-me-cfs-herzfrequenz%C3%BCberwachung-zur-vermeidung-von-pem-und-flare-ups/

    const val HEART_RATE_LIMIT = 90.0
    //const val HR_AT_REST = 75
    //const val HR_PENALTY_START = HR_AT_REST

    fun nextEnergyLevel10MinSimple(energy: Double, hr: Double): Double {
        val threshold: Double = HEART_RATE_LIMIT              // personal threshold (bpm)
        val depletionRate: Double = 10.0 / (60.0 * threshold)  // depletion factor
        val recoveryRate: Double = 0.05        // per hour
        val deltaT = 10.0 / 60.0              // timestep size in hours

        val load = max(0.0, hr - threshold)   // excess load from being over threshold
        val nextEnergy =
            (energy + recoveryRate * deltaT - depletionRate * load * deltaT).coerceIn(
                0.0,
                1.0
            )
        return nextEnergy
    }

    /**
     * Energy update for a 10-minute time step with *three* HR ranges:
     *
     *   HR > hrThresh     → linear discharge
     *   hrRest < HR < hrThresh → linear recovery
     *   HR < hrRest       → maximum recovery (cap)
     *
     * Everything is continuous; the sign changes at the threshold hrThresh.
     */

    fun nextEnergyLevel10MinAdvanced(
        energy: Double,                 // current energy in the step
        hr: Double,                     // current heart rate in the step
        hrThresh: Double = HEART_RATE_LIMIT,   // threshold above which energy decreases
        hrRest: Double = 50.0,          // sleep/rest HR below which energy charges the most
        maxRecoveryH: Double = 0.10,    // max. charge per hour (<1)
        depletionFactor: Double = 1.0 / (60.0 * hrThresh), // as before
        dtMin: Double = 10.0            // step size in minutes
    ): Double {

        val dtH = dtMin / 60.0                          // step size in hours

        // linear increase in recovery between hrThresh hrRest
        val recoverySlope = maxRecoveryH / (hrThresh - hrRest)

        val deltaEnergy = when {
            hr > hrThresh ->                       // Energy discharge range
                -depletionFactor * (hr - hrThresh) * dtH

            hr >= hrRest ->                        // Energy linear recover range
                +recoverySlope * (hrThresh - hr) * dtH

            else ->                                // maximum energy recovery (deep rest/sleep)
                +maxRecoveryH * dtH
        }

        return (energy + deltaEnergy).coerceIn(0.0, 1.0)
    }

    //TODO integrate minutely by lerping hr from one 10 min spot to next while 10 energy steps
    fun heartRate10MinToEnergy10min(
        startingEnergy: Double,
        heartRate10Min: FloatArray
    ): FloatArray {
        val energyLevels = FloatArray(heartRate10Min.size)
        var currentEnergy = startingEnergy.toDouble()

        for (i in heartRate10Min.indices) {
            currentEnergy = nextEnergyLevel10MinSimple(currentEnergy, heartRate10Min[i].toDouble())
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