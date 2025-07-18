package org.htwk.pacing.backend.heuristics

fun calculateNewEnergyLevel(currentEnergy: Double, symptomSeverityInput: Int): Double {
    val symptomSeverity = symptomSeverityInput.coerceIn(1..3)

    val reductionFactor =
        (2.5 * symptomSeverity * symptomSeverity + 7.5 * symptomSeverity - 5) / 100.0
    return currentEnergy * (1 - reductionFactor)
}
