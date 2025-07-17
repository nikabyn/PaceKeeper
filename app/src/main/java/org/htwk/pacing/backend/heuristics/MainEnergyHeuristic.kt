package org.htwk.pacing.backend.heuristics

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.heuristics.EnergyFromHeartRateCalculator.nextEnergyLevelFromHeartRate
import org.htwk.pacing.backend.heuristics.PemCalculator.calculatePemProbability
import org.htwk.pacing.ui.math.interpolate
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val WEAR_OFF_SPEED = 0.05f
private val PEM_WEAR_OFF_DAYS = 5

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

fun nextEnergyFromSymptomLevels(
    energy: Double,
    last: ManualSymptomEntry,
    now: Instant,
): Double {
    val feeling = last.feeling.feeling
    if (feeling == Feeling.VeryGood) return energy  // good symptoms, don't reduce energy

    val severity = severityByFeeling[feeling] ?: 1
    val fadeDur = fadeDaysBySeverity[severity] ?: 3.days

    val elapsed = now - last.feeling.time
    if (elapsed >= fadeDur) return energy          // already surpassed full fade

    val remainingFadedStrength = linearFadeOut(elapsed, fadeDur)
    val targetEnergyForSeverity = calculateNewEnergyLevel(energy, severity)

    return interpolate(energy, targetEnergyForSeverity, remainingFadedStrength * WEAR_OFF_SPEED)
        .coerceIn(0.0, 1.0)
}

fun nextEnergyFromPemProbability(
    currentEnergy: Double,
    last: ManualSymptomEntry,
    now: Instant
): Double {
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

fun mainEnergyHeuristic(
    energyBegin: Double,
    heartRate10MinSeries: FloatArray,
    symptomsFromLast3Days: List<ManualSymptomEntry>, now: Instant
): FloatArray {
    val energy10minSeries = FloatArray(heartRate10MinSeries.size)
    var currentEnergy = energyBegin.toDouble()

    for (i in heartRate10MinSeries.indices) {
        //continue;
        // A, 1.) HR based adjustment
        //TODO: base recharge if we have no energy available?
        currentEnergy =
            nextEnergyLevelFromHeartRate(
                currentEnergy, heartRate10MinSeries[i].toDouble()
            )


        // B) symptom based adjustments
        val timepoint = now + 10.minutes * i
        val latestSymptomEntry =
            symptomsFromLast3Days.filter { it.feeling.time <= timepoint }
                .maxByOrNull { it.feeling.time }

        //skip symptom based energy calculation if there's no most recent entry

        //TODO: don't skip other calculations
        if (latestSymptomEntry != null && false) {
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
