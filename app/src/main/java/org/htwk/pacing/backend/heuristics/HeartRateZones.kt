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

    private fun calculateMaxHeartRate(age: Int, gender: Gender): Double {
        return when (gender) {
            Gender.MALE -> 223 - (0.9 * age)
            Gender.FEMALE -> 223 - age
        } as Double
    }

    private fun calculateAnaerobicThreshold(maxHeartRate: Double): Double {
        return maxHeartRate * 0.55
    }

    fun calculateZones(input: HeartRateInput): HeartRateZonesResult {
        val maxHR = calculateMaxHeartRate(input.age, input.gender)
        val threshold = calculateAnaerobicThreshold(maxHR)

        // Sicherstellen, dass die Werte aufsteigend sind
        val healthUpper = maxOf(input.restingHeartRate + 1, (input.restingHeartRate * 1.10).toInt())
        val recoveryUpper = maxOf(healthUpper + 1, (input.restingHeartRate * 1.20).toInt())
        val exertionUpper = maxOf(recoveryUpper + 1, threshold.toInt())

        // Validieren, dass exertionUpper nicht größer als maxHR ist
        val safeExertionUpper = minOf(exertionUpper, maxHR.toInt() - 1)

        return HeartRateZonesResult(
            maxHeartRate = maxHR,
            anaerobicThreshold = threshold,
            healthZone = input.restingHeartRate..healthUpper,
            recoveryZone = (healthUpper + 1)..recoveryUpper, // Überschneidungen vermeiden
            exertionZone = (recoveryUpper + 1)..safeExertionUpper
        )
    }
}


