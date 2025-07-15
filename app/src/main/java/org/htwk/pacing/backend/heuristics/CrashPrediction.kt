package org.htwk.pacing.backend.heuristics
import org.htwk.pacing.backend.heuristics.DavenportData

/**
 * @param observedSymptoms Liste der beobachteten Symptome als Strings
 * @return Wahrscheinlichkeit für PEM (zwischen 0.0 und 1.0)
 */

object PemCalculator {

    private const val PRIOR_PEM = 0.8
    private const val PRIOR_NOT_PEM = 1.0 - PRIOR_PEM

    fun calculatePemProbability(observedSymptoms: List<String>): Double {
        val matchedSymptoms = observedSymptoms.mapNotNull { DavenportData.findSymptom(it) }

        // Wenn kein bekanntes Symptom gefunden wurde, Rückgabe 0.0
        if (matchedSymptoms.isEmpty()) return 0.0

        var likelihoodGivenPem = 1.0
        var likelihoodGivenNotPem = 1.0

        matchedSymptoms.forEach { symptom ->
            likelihoodGivenPem *= symptom.prevalenceME
            likelihoodGivenNotPem *= symptom.prevalenceControl
        }

        val numerator = likelihoodGivenPem * PRIOR_PEM
        val denominator = numerator + (likelihoodGivenNotPem * PRIOR_NOT_PEM)

        return if (denominator == 0.0) 0.0 else numerator / denominator
    }
}
