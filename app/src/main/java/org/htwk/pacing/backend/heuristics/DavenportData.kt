package org.htwk.pacing.backend.heuristics

data class SymptomData(
    val symptom: String,
    val prevalenceME: Double,
    val prevalenceControl: Double,
)

object DavenportData {
    val symptomList: List<SymptomData> = listOf(
        SymptomData("Fatigue", 0.95, 0.10),
        SymptomData("Brain Fog", 0.85, 0.05),
        SymptomData("Muscle Pain", 0.75, 0.15),
        SymptomData("Sleep Problems", 0.70, 0.20)
    )

    fun findSymptom(searchTerm: String): SymptomData? {
        return symptomList.find { it.symptom.contains(searchTerm, ignoreCase = true) }
    }
}
