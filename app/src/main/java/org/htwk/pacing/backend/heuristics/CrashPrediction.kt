package org.htwk.pacing.backend.heuristics
import org.htwk.pacing.backend.heuristics.DavenportData
/**
 *
 * @param symptoms Liste der beobachteten Symptome
 * @param pPEM A-priori-Wahrscheinlichkeit für PEM (z. B. 0.7)
 * @param pSymptomGivenPEM Map mit P(Symptom | PEM) für jedes Symptom
 * @param pSymptom Map mit P(Symptom) für jedes Symptom
 * @return Wahrscheinlichkeit für PEM gegeben die Symptome
 */
fun calculatePEMProbability(
    symptoms: List<String>,
    pPEM: Double,
    pSymptomGivenPEM: Map<String, Double>,
    pSymptom: Map<String, Double>
): Double {
    var numerator = pPEM
    var denominator = 1.0

    for (symptom in symptoms) {
        val pGivenPEM = pSymptomGivenPEM[symptom] ?: continue
        val pSym = pSymptom[symptom] ?: continue

        numerator *= pGivenPEM
        denominator *= pSym
    }

    return if (denominator != 0.0) numerator / denominator else 0.0
}
