package org.htwk.pacing.backend.heuristics

data class SymptomData(
    val symptom: String,
    val prevalenceME: Double,
    val prevalenceControl: Double,
)

object DavenportData {
    val symptomList: List<SymptomData> = listOf(
        SymptomData("Cognitive Dysfunction", 0.286, 0.00),
        SymptomData("Kognitive Dysfunktion", 0.286, 0.00),
        SymptomData("Wortfindungsstörung", 0.286, 0.00),
        SymptomData("Konzentrationsprobleme", 0.286, 0.00),
        SymptomData("Gedächnisprobleme", 0.286, 0.00),
        SymptomData("Brain Fog", 0.286, 0.00),
        SymptomData("Low Mood", 0.918, 0.545),
        SymptomData("Schlechte Laune", 0.918, 0.545),
        SymptomData("Gereiztheit", 0.918, 0.545),
        SymptomData("Decrease in Function", 0.245, 0.0),
        SymptomData("Allgemeine Funktionsabnahme", 0.245, 0.0)
    )

    fun findSymptom(searchTerm: String): SymptomData? {
        return symptomList.find { it.symptom.contains(searchTerm, ignoreCase = true) }
    }
}
