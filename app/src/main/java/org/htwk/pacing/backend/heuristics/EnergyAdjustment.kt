package org.htwk.pacing.backend.heuristics

fun calculateNewEnergyLevel(currentEnergy: Double, symptomSeverity: Int): Double {
    symptomSeverity.coerceIn(1..3)

    val reductionFactor =
        (2.5 * symptomSeverity * symptomSeverity + 7.5 * symptomSeverity - 5) / 100.0
    return currentEnergy * (1 - reductionFactor)
}
