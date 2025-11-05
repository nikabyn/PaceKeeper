package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry

data class GenericTimedDataPoint(
    val time: Instant,
    val value: Double
) {
    constructor(src: HeartRateEntry) : this(
        time = src.time,
        value = src.bpm.toDouble()
    )

    constructor(src: DistanceEntry) : this(
        time = src.end,
        value = src.length.inMeters().toDouble()
    ) // example: magnitude
}