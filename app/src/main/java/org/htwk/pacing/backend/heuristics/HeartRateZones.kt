package org.htwk.pacing.backend.heuristics

object HeartRateZones {

    enum class Gender {
        MALE, FEMALE
    }

    data class HeartRateInput(
        val age: Int,
        val gender: Gender,
        val restingHeartRate: Int
    )

    data class HeartRateZonesResult(
        val maxHeartRate: Double,
        val anaerobicThreshold: Double,
        val healthZone: IntRange,
        val recoveryZone: IntRange,
        val exertionZone: IntRange
    )

    fun calculateMaxHeartRate(age: Int, gender: Gender): Double {
        return when (gender) {
            Gender.MALE -> 223.0 - (0.9 * age)
            Gender.FEMALE -> 223.0 - age
        }
    }

    fun calculateAnaerobicThreshold(maxHeartRate: Double): Double {
        return maxHeartRate * 0.55
    }


    fun calculateZones(input: HeartRateInput): HeartRateZonesResult {
        val maxHR = calculateMaxHeartRate(input.age, input.gender)
        val threshold = calculateAnaerobicThreshold(maxHR)

        //val healthZoneLower = maxOf(30, (input.restingHeartRate * 0.90).toInt())

        // Berechne die Zonen basierend auf Prozentsätzen vom Ruhepuls
        val healthZoneUpper = (input.restingHeartRate * 1.10).toInt() // 0-10%
        val recoveryZoneUpper = (input.restingHeartRate * 1.20).toInt() // 10-20%
        val exertionZoneUpper = threshold.toInt() // 20% bis AS

        // Sicherstellen, dass die Werte aufsteigend sind und keine Überschneidungen
        val safeHealthUpper = maxOf(input.restingHeartRate + 1, healthZoneUpper)
        val safeRecoveryUpper = maxOf(safeHealthUpper + 1, recoveryZoneUpper)
        val safeExertionUpper = maxOf(safeRecoveryUpper + 1, exertionZoneUpper)

        // Validieren, dass die obere Grenze nicht über der maximalen Herzfrequenz liegt
        val finalExertionUpper = minOf(safeExertionUpper, maxHR.toInt() - 1)

        return HeartRateZonesResult(
            maxHeartRate = maxHR,
            anaerobicThreshold = threshold,
            healthZone = 0..safeHealthUpper,
            recoveryZone = (safeHealthUpper + 1)..safeRecoveryUpper,
            exertionZone = (safeRecoveryUpper + 1)..safeExertionUpper
        )
    }
}


