package org.htwk.pacing.backend.heuristics

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Feeling
import org.htwk.pacing.backend.database.ManualSymptomEntry
import org.htwk.pacing.backend.heuristics.PemCalculator.calculatePemProbability
import org.htwk.pacing.ui.math.interpolate
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

fun nextEnergyFromSymptomLevels(
    currentEnergy: Double,
    symptomEntries: List<ManualSymptomEntry>,
    now: Instant,
): Double {
    //find latest symptom Entry before now
    val latestSymptomEntry =
        symptomEntries.filter { it.feeling.time <= now }.maxByOrNull { it.feeling.time }

    if (latestSymptomEntry == null) return currentEnergy

    val symptomsTime = latestSymptomEntry.feeling.time
    val symptoms = latestSymptomEntry.symptoms
    val feeling = latestSymptomEntry.feeling.feeling

    //don't reduce energy if symptoms good
    if (feeling == Feeling.VeryGood) return currentEnergy;

    val severity: Int = (3 - feeling.level).coerceIn(1, 3)

    //for (symptom in symptoms) {
    val timeSinceSymptom = (now - symptomsTime)
    val assumedRemainingStrength =
        ((severity.days.inWholeSeconds - timeSinceSymptom.inWholeSeconds).toDouble() / 3.days.inWholeSeconds.toDouble())
            .coerceIn(0.0, 1.0)
    val newEnergy =
        interpolate(
            currentEnergy,
            calculateNewEnergyLevel(currentEnergy, severity),
            assumedRemainingStrength.toFloat() * 0.05f
        )

    return newEnergy.coerceIn(0.0, 1.0)
}

fun energyFromPemProbability(symptomEntries: List<ManualSymptomEntry>): Double {
    val symptomNames: List<String> = symptomEntries.flatMap { it.symptoms.map { s -> s.name } }
    val symptomNamesDistinct = symptomNames.distinct()
    val pemProbability = calculatePemProbability(symptomNamesDistinct)
    return 1.0 - pemProbability
}


fun mainEnergyHeuristic(
    energyBegin: Double,
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

    //val energy = heartRate10MinToEnergy10min(energyBegin, heartRate10MinSeries)

    //energyFromSymptomLevels

    //energyFromPemProbability

    //energyFromHeartRate

    val energy10minSeries = FloatArray(heartRate10MinSeries.size)
    var currentEnergy = energyBegin.toDouble()

    //TODO integrate minutely by lerping hr from one 10 min spot to next while 10 energy steps
    for (i in heartRate10MinSeries.indices) {
        /*currentEnergy =
            nextEnergyLevel10MinSimple(
                currentEnergy, heartRate10MinSeries[i].toDouble()
            )*/

        currentEnergy =
            nextEnergyFromSymptomLevels(currentEnergy, symptomEntries, now + 10.minutes * i)

        val pemEnergyPenalty = energyFromPemProbability(symptomEntries)

        energy10minSeries[i] = currentEnergy.toFloat()
    }
    return energy10minSeries
}
