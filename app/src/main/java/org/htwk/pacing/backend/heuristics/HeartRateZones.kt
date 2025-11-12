package org.htwk.pacing.backend.heuristics

object HeartRateZones {

    enum class Sex {
        MALE, FEMALE
    }

    data class HeartRateInput(
        val age: Int,
        val sex: Sex,
        val restingHeartRate: Int
    )

    data class HeartRateZonesResult(
        val maxHeartRate: Double,
        val anaerobicThreshold: Double,
        val healthZone: IntRange,
        val visualHealthZone: IntRange,
        val recoveryZone: IntRange,
        val exertionZone: IntRange
    )

    fun calculateMaxHeartRate(age: Int, sex: Sex): Double {
        return when (sex) {
            Sex.MALE -> 223.0 - (0.9 * age)
            Sex.FEMALE -> 223.0 - age
        }
    }

    fun calculateAnaerobicThreshold(maxHeartRate: Double): Double {
        return maxHeartRate * 0.55
    }


    fun calculateZones(input: HeartRateInput): HeartRateZonesResult {
        val maxHR = calculateMaxHeartRate(input.age, input.sex)
        val threshold = calculateAnaerobicThreshold(maxHR)


        // Calculate Zones based on restingHeartRate
        val healthZoneUpper = (input.restingHeartRate * 1.10).toInt() // 0-10%
        val recoveryZoneUpper = (input.restingHeartRate * 1.20).toInt() // 10-20%
        val exertionZoneUpper = threshold.toInt() // 20% bis AS
        val healthZoneLower = maxOf(30, (input.restingHeartRate * 0.90).toInt())

        // Check order of zones
        val safeHealthUpper = maxOf(input.restingHeartRate + 1, healthZoneUpper)
        val safeRecoveryUpper = maxOf(safeHealthUpper + 1, recoveryZoneUpper)
        val safeExertionUpper = maxOf(safeRecoveryUpper + 1, exertionZoneUpper)

        // Validate that upper limit does not exceed maxHeartRate
        val finalExertionUpper = minOf(safeExertionUpper, maxHR.toInt() - 1)

        return HeartRateZonesResult(
            maxHeartRate = maxHR,
            anaerobicThreshold = threshold,
            healthZone = healthZoneLower..safeHealthUpper,
            visualHealthZone = 0..safeHealthUpper,
            recoveryZone = (safeHealthUpper + 1)..safeRecoveryUpper,
            exertionZone = (safeRecoveryUpper + 1)..finalExertionUpper
        )
    }
}


