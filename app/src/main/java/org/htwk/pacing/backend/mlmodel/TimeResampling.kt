package org.htwk.pacing.backend.mlmodel

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import kotlin.time.Duration.Companion.minutes

fun truncateTo10Minutes(instant: Instant): Instant {
    val truncatedEpochSeconds = instant.toEpochMilliseconds() - (instant.toEpochMilliseconds() % 600_000)
    return Instant.fromEpochSeconds(truncatedEpochSeconds)
}


fun resampleHRTo10MinIntervals(entries: List<HeartRateEntry>): List<HeartRateEntry> {
    if (entries.isEmpty()) return emptyList<HeartRateEntry>()

    val resampled = mutableListOf<HeartRateEntry>()
    // Sort by timestamp if not already sorted
    val sortedEntries = entries.sortedBy { it.time }

    var current10MinIntervalStart = truncateTo10Minutes(sortedEntries.first().time)
    var sumInInterval = 0.0
    var countInInterval = 0

    for (entry in sortedEntries) {
        if (entry.time < current10MinIntervalStart + 10.minutes) {
            sumInInterval += entry.bpm
            countInInterval++
        }
        else //we have reached the next interval
        {
            // Finalize previous interval
            if (countInInterval > 0) {
                resampled.add(
                    HeartRateEntry(
                        time = current10MinIntervalStart,
                        bpm = (sumInInterval / countInInterval).toLong()
                    )
                )
            }

            // Start new interval, process first entry
            current10MinIntervalStart += 10.minutes
            sumInInterval = entry.bpm.toDouble()
            countInInterval = 1
        }
    }

    // Add the last interval, in case it's less than 10 mins long
    if (countInInterval > 0) {
        resampled.add(
            HeartRateEntry(
                time = current10MinIntervalStart,
                bpm = (sumInInterval / countInInterval).toLong()
            )
        )
    }

    return resampled
}