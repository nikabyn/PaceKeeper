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