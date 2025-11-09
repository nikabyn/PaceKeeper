package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry

/**
 * A generic representation of a data point that has a specific time and a numeric value.
 * This class serves as a standardized format for various types of vital data,
 * such as heart rate or distance, allowing them to be processed uniformly.
 *
 * @property time The [Instant] at which the data point was recorded.
 * @property value The numeric value of the data point, represented as a [Double].
 */
data class GenericTimedDataPoint(
    val time: Instant,
    val value: Double
) {
    /**
     * Secondary constructor to create a [GenericTimedDataPoint] from a [HeartRateEntry].
     * @param src The source [HeartRateEntry].
     */
    constructor(src: HeartRateEntry) : this(
        time = src.time,
        value = src.bpm.toDouble()
    )

    /**
     * Secondary constructor to create a [GenericTimedDataPoint] from a [DistanceEntry].
     * The time is taken from the end of the distance interval, and the value is the length in meters.
     * @param src The source [DistanceEntry].
     */
    constructor(src: DistanceEntry) : this(
        time = src.end,
        value = src.length.inMeters()
    )
}