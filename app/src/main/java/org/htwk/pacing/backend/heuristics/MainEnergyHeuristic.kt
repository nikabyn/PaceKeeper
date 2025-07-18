package org.htwk.pacing.backend.heuristics

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.heuristics.EnergyFromHeartRateCalculator.nextEnergyLevelFromHeartRate
import org.htwk.pacing.backend.heuristics.MainEnergyHeuristicCalculator.PEM_WEAR_OFF_DAYS
import org.htwk.pacing.backend.heuristics.MainEnergyHeuristicCalculator.fadeDaysBySeverity
import org.htwk.pacing.backend.heuristics.PemCalculator.calculatePemProbability
import org.htwk.pacing.ui.math.interpolate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

object MainEnergyHeuristicCalculator {
    private const val WEAR_OFF_SPEED = 0.05f
    private const val PEM_WEAR_OFF_DAYS = 5

    /**
     * Returns a factor between 1.0 (at start) and 0.0 (at end, faded‑out) that drops
     * linearly with time.
     *
     * @param elapsed     How much time has passed since the event.
     * @param totalSpan   Duration after which the effect is considered 0.
     */
    private fun linearFadeOut(elapsed: Duration, totalSpan: Duration): Float {
        return (1.0f - elapsed.inWholeSeconds.toFloat() /
                totalSpan.inWholeSeconds.toFloat())
            .coerceIn(0.0f, 1.0f)
    }

    //more severe symptoms should take longer to fade out, this map defines how long it should take
    private val fadeDaysBySeverity = mapOf(
        1 to 1.days,   // mild
        2 to 3.days,   // moderat
        3 to 5.days    // stark / Crash
    )

    //worse feeling means higher severity
    private val severityByFeeling = mapOf(
        Feeling.VeryGood to 0,
        Feeling.Good to 1,
        Feeling.Bad to 2,
        Feeling.VeryBad to 3
    )


    /**
     * Calculates the next energy level based on symptom levels.
     *
     * The energy level is adjusted based on the severity of the last reported symptoms.
     * The effect of symptoms fades linearly over a period defined by [fadeDaysBySeverity].
     * If the feeling is VeryGood (best), the energy level remains unchanged.
     *
     * @param currentEnergy The current energy level.
     * @param last The last manual symptom entry.
     * @param now The current timestamp.
     * @return The new energy level (between 0.0 and 1.0), adjusted for symptom severity.
     *         Returns [energy] if the symptom duration has already been exceeded or if the feeling is VeryGood.
     *
     */
    fun nextEnergyFromSymptomLevels(
        currentEnergy: Double,
        last: ManualSymptomEntry,
        now: Instant,
    ): Double {
        //skip if any part of feeling not available
        if (last == null || last.feeling == null || last.feeling.feeling == null) return currentEnergy

        val feeling = last.feeling.feeling
        if (feeling == Feeling.VeryGood) return currentEnergy  // good symptoms, dont reduce energy

        val severity = severityByFeeling[feeling] ?: 1
        val fadeDur = fadeDaysBySeverity[severity] ?: 3.days

        val elapsed = now - last.feeling.time
        if (elapsed >= fadeDur) return currentEnergy          // already surpassed full fade

        val remainingFadedStrength = linearFadeOut(elapsed, fadeDur)
        val targetEnergyForSeverity = calculateNewEnergyLevel(currentEnergy, severity)

        return interpolate(
            currentEnergy,
            targetEnergyForSeverity,
            remainingFadedStrength * WEAR_OFF_SPEED
        )
            .coerceIn(0.0, 1.0)
    }


    /**
     * Calculates the next energy level based on the probability of Post-Exertional Malaise (PEM).
     *
     * The energy level is reduced based on the PEM probability, which fades linearly over
     * [PEM_WEAR_OFF_DAYS].
     *
     * @param currentEnergy The current energy level
     * @param last The last manual symptom entry.
     * @param now The current timestamp.
     * @return The new energy level (between 0.0 and 1.0), adjusted for PEM probability.
     *         Returns [currentEnergy] if the PEM duration has already been exceeded.
     */
    fun nextEnergyFromPemProbability(
        currentEnergy: Double,
        last: ManualSymptomEntry,
        now: Instant
    ): Double {
        if (last == null || last.symptoms == null) return currentEnergy

        val fadeDays = PEM_WEAR_OFF_DAYS.days

        val elapsed = now - last.feeling.time
        if (elapsed >= fadeDays) return currentEnergy //PEM Duration already exceeded

        val pemProbability = calculatePemProbability(
            last.symptoms.map { it.name }.distinct()
        )

        val remainingFadedStrength = linearFadeOut(elapsed, fadeDays)

        val penalty = pemProbability * remainingFadedStrength
        return (currentEnergy - penalty).coerceIn(0.0, 1.0)
    }


    /**
     * Calculates the main energy heuristic step by step, based on various factors.
     *
     * This function considers the initial energy level, heart rate data over the last 10 minutes,
     * and manual symptom entries from the last 3 days to determine the current energy level.
     *
     * @param energyBegin The initial energy level.
     * @param heartRate10MinSeries An array of heart rate values recorded over the last 10 minutes.
     * @param symptomsFromLast3Days A list of manual symptom entries from the last 3 days.
     * @param now The current timestamp.
     * @return A float array representing the energy levels in 10 minute intervals
     *
     */
    fun mainEnergyHeuristic(
        energyBegin: Double,
        heartRate10MinSeries: FloatArray,
        symptomsFromLast3Days: List<ManualSymptomEntry>, now: Instant
    ): FloatArray {
        val energy10minSeries = FloatArray(heartRate10MinSeries.size)
        var currentEnergy = energyBegin.toDouble()

        for (i in heartRate10MinSeries.indices) {
            // A, 1.) HR based adjustment
            //IDEA: mathematically separate/extract base recharge from nextEnergyLevelFromHeartRate
            currentEnergy =
                nextEnergyLevelFromHeartRate(
                    currentEnergy, heartRate10MinSeries[i].toDouble()
                )
            
            // B) symptom based adjustments
            val timepoint = now + (10.minutes * i)
            val latestSymptomEntry =
                symptomsFromLast3Days.filter { it.feeling.time <= timepoint }
                    .maxByOrNull { it.feeling.time }

            //skip symptom based energy calculation if there's no most recent entry

            if (latestSymptomEntry != null) {
                // 2.) symptom based adjustment
                currentEnergy =
                    nextEnergyFromSymptomLevels(currentEnergy, latestSymptomEntry!!, timepoint)

                // 3.) pem based adjustment
                val pemEnergyPenalty =
                    nextEnergyFromPemProbability(currentEnergy, latestSymptomEntry!!, timepoint)
            }

            energy10minSeries[i] = currentEnergy.toFloat()
        }
        return energy10minSeries
    }
}
