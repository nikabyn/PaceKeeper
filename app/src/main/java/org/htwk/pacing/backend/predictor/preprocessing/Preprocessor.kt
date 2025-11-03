package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesList
import kotlin.time.Duration.Companion.hours

object Preprocessor {
    fun run(raw: MultiTimeSeriesList): Predictor.MultiTimeSeriesSamples {
        // generic cleaning of data
        fun <T, R : Comparable<R>> cleanData(
            list: List<T>,
            sortKey: (T) -> R,
            isInvalid: (T) -> Boolean,
            replaceInvalid: (T) -> T
        ): Pair<MutableList<T>, Double> {
            var correctionCount = 0

            val cleanedList = list
                .sortedBy(sortKey)
                .distinct()
                .map {
                    if (isInvalid(it)) {
                        correctionCount++
                        replaceInvalid(it)
                    } else it
                }
                .toMutableList()

            val correctionRatio = if (cleanedList.isNotEmpty()) {
                correctionCount.toDouble() / cleanedList.size
            } else 0.0

            return Pair(cleanedList, correctionRatio)
        }

        val (cleanedHeartRates, _) = cleanData(
            list = raw.heartRate,
            sortKey = { it.time },
            isInvalid = { it.bpm < 30 || it.bpm > 220 },
            replaceInvalid = { HeartRateEntry(it.time, 0) }
        )

        val (cleanedDistances, _) = cleanData(
            list = raw.distance,
            sortKey = { it.start },
            isInvalid = { it.length.inMeters() < 0 },
            replaceInvalid = { DistanceEntry(it.start, it.end, length = Length(0.0)) }
        )

        // TODO: see other ticket
        return Predictor.MultiTimeSeriesSamples(
            timeStart = Clock.System.now() - 6.hours,
            heartRate  = cleanedHeartRates.map { it.bpm.toFloat() }.toFloatArray(),
            distance = cleanedDistances.map { it.length.inMeters().toFloat() }.toFloatArray()
        )
    }
}