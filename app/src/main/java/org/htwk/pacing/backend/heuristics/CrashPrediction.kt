package org.htwk.pacing.backend.heuristics

object PemCalculator {

    // vorläufige Konstante, ggf. über erfasste Metriken berechnen
    private const val PRIOR_PEM = 0.8
    private const val PRIOR_NOT_PEM = 1.0 - PRIOR_PEM

    fun calculatePemProbability(observedSymptoms: List<String>): Double {
        val matchedSymptoms = observedSymptoms.mapNotNull { DavenportData.findSymptom(it) }
        val epsilon = 1e-6
        // Wenn kein bekanntes Symptom gefunden wurde, Rückgabe 0.0
        if (matchedSymptoms.isEmpty()) return 0.0

        var likelihoodGivenPem = 1.0
        var likelihoodGivenNotPem = 1.0

        matchedSymptoms.forEach { symptom ->
            likelihoodGivenPem *= symptom.prevalenceME.coerceAtLeast(epsilon)
            likelihoodGivenNotPem *= symptom.prevalenceControl.coerceAtLeast(epsilon)
        }

        val numerator = likelihoodGivenPem * PRIOR_PEM
        val denominator = numerator + (likelihoodGivenNotPem * PRIOR_NOT_PEM)

        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
}
