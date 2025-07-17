package org.htwk.pacing.backend.heuristics

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.heuristics.PemCalculator.calculatePemProbability
import org.htwk.pacing.ui.math.interpolate
import kotlin.time.Duration.Companion.days

fun energyFromSymptomLevels(
    symptomEntries: List<ManualSymptomEntry>,
    now: Instant
): Double {
    var energy = 1.0

    for (symptomEntry in symptomEntries) {
        val symptomsTime = symptomEntry.feeling.time
        val symptoms = symptomEntry.symptoms
        val feeling = symptomEntry.feeling.feeling
        val level: Int = feeling.level

        if (!(feeling.level in 1..3)) continue;

        for (symptom in symptoms) {
            val assumedRemaingStrength =
                (level.days.inWholeSeconds - (now - symptomsTime).inWholeSeconds).toDouble() / 3.days.inWholeSeconds.toDouble()
            energy =
                interpolate(
                    energy,
                    calculateNewEnergyLevel(energy, feeling.level),
                    assumedRemaingStrength.toFloat()
                )
        }
    }

    return energy
}

fun energyFromPemProbability(symptomEntries: List<ManualSymptomEntry>): Double {
    val symptomNames: List<String> = symptomEntries.flatMap { it.symptoms.map { s -> s.name } }
    val symptomNamesDistinct = symptomNames.distinct()
    val pemProbability = calculatePemProbability(symptomNamesDistinct)
    return 1.0 - pemProbability
}

fun mainEnergyHeuristic(
    heartRate10MinSeries: FloatArray,
    symptomEntries: List<ManualSymptomEntry>, now: Instant
): FloatArray {
    /*TODO: turn PredictedEnergyLevel into EnergyLevel again
        write both future and past energy
        start heuristic at now - 12 hours, using the bpm at that point as a starting point
        alternatively, just use the first existing datapoint and then overwrite
        -> if no data exists in the db yet, overwrite with constant 0.5
        -> this will then be the starting point of the above process
    * */

    //energyFromSymptomLevels

    //energyFromPemProbability

    //energyFromHeartRate

    return floatArrayOf(0.0f)
}
