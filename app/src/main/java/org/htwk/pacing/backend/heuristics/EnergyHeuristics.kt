package org.htwk.pacing.backend.heuristics

import org.htwk.pacing.backend.database.HeartRateEntry
import kotlin.math.max
import kotlin.math.min

//TODO: placeholder, to be replaced by scientific model trough ui feature ticket #21
fun energyLevelFromHeartRate(heartRate: Double, /*liste von symptomen*/): Double {
    val heartRateClipped = min(50.0, max(heartRate, 150.0) - 50.0);
    return (100.0 - heartRateClipped) / 100.0;
}