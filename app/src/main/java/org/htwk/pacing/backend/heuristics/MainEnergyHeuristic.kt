package org.htwk.pacing.backend.heuristics

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.heuristics.EnergyFromHeartRateCalculator.nextEnergyLevelFromHeartRate
import org.htwk.pacing.backend.heuristics.PemCalculator.calculatePemProbability
import org.htwk.pacing.ui.math.interpolate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

private val WEAR_OFF_SPEED = 0.05f
private val SYMPTOM_WEAR_OFF_DAYS = 5

fun nextEnergyFromSymptomLevels(
    currentEnergy: Double,
    latestSymptomEntry: ManualSymptomEntry,
    now: Instant,
): Double {
    val feeling = latestSymptomEntry.feeling.feeling
    //don't reduce energy if symptoms good
    if (feeling == Feeling.VeryGood) return currentEnergy;

    val severity: Int = (3 - feeling.level).coerceIn(1, 3)

    val timeSinceSymptom = (now - latestSymptomEntry.feeling.time)

    val assumedRemainingStrength =
        ((severity.days.inWholeSeconds - timeSinceSymptom.inWholeSeconds).toDouble() / 3.days.inWholeSeconds.toDouble())
            .coerceIn(0.0, 1.0)

    val newEnergy =
        interpolate(
            currentEnergy,
            calculateNewEnergyLevel(currentEnergy, severity),
            assumedRemainingStrength.toFloat() * WEAR_OFF_SPEED
        )

    return newEnergy.coerceIn(0.0, 1.0)
}

fun nextEnergyFromPemProbability(
    currentEnergy: Double,
    latestSymptomEntry: ManualSymptomEntry,
    now: Instant
): Double {
    val timeSinceEntry = now - latestSymptomEntry.feeling.time
    //no pem last pem is too long ago
    if (timeSinceEntry > SYMPTOM_WEAR_OFF_DAYS.days) return currentEnergy

    val symptomNames = latestSymptomEntry.symptoms.map { it.name }.distinct()
    val pemProbability = calculatePemProbability(symptomNames)

    val timeDecayFactor =
        maxOf(
            0.0,
            1.0 - (timeSinceEntry.inWholeSeconds.toDouble() / SYMPTOM_WEAR_OFF_DAYS.days.inWholeSeconds.toDouble())
        )

    val remainingPemProbability = interpolate(pemProbability, 0.0, timeDecayFactor.toFloat())

    return remainingPemProbability
}

fun mainEnergyHeuristic(
    energyBegin: Double,
    heartRate10MinSeries: FloatArray,
    symptomsFromLast3Days: List<ManualSymptomEntry>, now: Instant
): FloatArray {
    /*TODO: turn PredictedEnergyLevel into EnergyLevel again
        write both future and past energy
        start heuristic at now - 12 hours, using the bpm at that point as a starting point
        alternatively, just use the first existing datapoint and then overwrite
        -> if no data exists in the db yet, overwrite with constant 0.5
        -> this will then be the starting point of the above process
    * */

    val energy10minSeries = FloatArray(heartRate10MinSeries.size)
    var currentEnergy = energyBegin.toDouble()

    for (i in heartRate10MinSeries.indices) {
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
