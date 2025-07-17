package org.htwk.pacing.backend.heuristics

object EnergyFromHeartRateCalculator {
    //good heuristic for anaerobic threshold limit for ME/CFS patients in general
    //observed to be a bit higher than healthy people
    const val HR_REST = 75.0

    //margin of 15 bpm before we reach threshold
    // see https://www.millionsmissing.de/2024/10/08/pacing-bei-me-cfs-herzfrequenz%C3%BCberwachung-zur-vermeidung-von-pem-und-flare-ups/
    const val HR_MARGIN = 15.0
    const val HR_THRESHOLD = HR_REST + HR_MARGIN

    const val FULL_RECHARGE_HOURS = 48.0

    //in ME/CFS Patients, recovery usually takes 8 times as long as associated depletion
    const val ACTIVITY_RECOVERY_RATIO = 8.0

    fun nextEnergyLevelFromHeartRate(energy: Double, hr: Double): Double {
        val deltaT = 10.0 / 60.0              // timestep size from one energy calc step to next

        val baseRate = 1.0 / FULL_RECHARGE_HOURS

        var load = (HR_THRESHOLD - hr) / (HR_MARGIN * 2)
        
        // the patient will loose energy much faster than regaining
        if (load < 0.0) load *= ACTIVITY_RECOVERY_RATIO

        //don't load up energy until we're at least resting
        if (hr in HR_REST..HR_THRESHOLD) load = 0.0
        val nextEnergy =
            (energy + baseRate * load * deltaT).coerceIn(
                0.0,
                1.0
            )
        return nextEnergy
    }
}