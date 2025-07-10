package org.htwk.pacing.backend

import java.time.LocalDateTime

//Hinzufügen der entsprechenden Daten
data class FitnessData(
    val timestamp: LocalDateTime,
    val activityType: String,
    val durationMinutes: Int,
    val calories: Int,
    val heartRate: Int
)

