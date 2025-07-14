package org.htwk.pacing.backend.heuristics

import org.htwk.pacing.backend.database.HeartRateEntry
import kotlin.math.max
import kotlin.math.min

fun energyLevelFromSymptoms(/* to be added */): Double {
    return 0.5
}

fun energyLevelFromHRZones(/* calculate from heart rate history/future, aka. a time series of heart rates*/): Double {
    return 0.5
}

// dummy calculation, to be replaced by scientific model
fun energyLevelFromHeartRate(heartRate: Double, /*liste von symptomen*/): Double {
    val heartRateClipped = min(50.0, max(heartRate, 150.0) - 50.0);
    return (100.0 - heartRateClipped) / 100.0;
}

data class HeartRateZone(
    val bpmMin: Long,
    val bpmMax: Long,
)

fun computeHeartRateZoneDistribution(heartRateData : List<HeartRateEntry>, heartRateZones : List<HeartRateZone>)
: List<Pair<HeartRateZone, Double>> {
    if (heartRateData.isEmpty()) {
        return heartRateZones.map { it to 0.0 }
    }

    val zoneCounts = MutableList(heartRateZones.size) { 0L }

    //TODO: weighted counting (similar to weighted average for 10-minute-intervals for model input)
    for (entry in heartRateData) {
        for ((index, zone) in heartRateZones.withIndex()) {
            if (entry.bpm >= zone.bpmMin && entry.bpm <= zone.bpmMax) {
                zoneCounts[index]++
                break // Move to the next heart rate entry once a zone is found for it
            }
        }
    }

    val totalEntries = heartRateData.size.toDouble()
    return heartRateZones.zip(zoneCounts).map { (zone, count) ->
        val relativeAmount = if (totalEntries > 0) (count.toDouble() / totalEntries) else 0.0
        zone to relativeAmount
    }
}
