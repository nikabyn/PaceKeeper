package org.htwk.pacing.backend.heuristics

import org.junit.Assert.*
import org.junit.Test

class PemCalculatorTest {

    @Test
    fun calculatePemProbability_withKnownSymptoms_returnsProbability() {
        val symptoms = listOf("Brain Fog", "Low Mood")
        val probability = PemCalculator.calculatePemProbability(symptoms)
        assertTrue(probability > 0.0)
        assertTrue(probability <= 1.0)
    }

    @Test
    fun calculatePemProbability_withUnknownSymptoms_returnsZero() {
        val symptoms = listOf("Unbekanntes Symptom")
        val probability = PemCalculator.calculatePemProbability(symptoms)

        assertEquals(0.0, probability, 0.0001)
    }

    @Test
    fun calculatePemProbability_withMixedSymptoms_stillWorks() {
        val symptoms = listOf("Low Mood", "Unknown")
        val probability = PemCalculator.calculatePemProbability(symptoms)
        assertTrue(probability > 0.0)
    }

    @Test
    fun calculatePemProbability_withEmptyList_returnsZero() {
        val probability = PemCalculator.calculatePemProbability(emptyList())

        assertEquals(0.0, probability, 0.0001)
    }
}
