package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.predictor.Predictor

//TODO: add pattern-matching-based invalid data sanitization, so that for different kinds of errors we can respond in different ways
fun cleanInputData(raw: Predictor.MultiTimeSeriesEntries): Pair<Predictor.MultiTimeSeriesEntries, IPreprocessor.QualityRatios> {
    // generic cleaning of data
    fun <T> cleanData(
        list: List<T>,
        timeSortKey: (T) -> Instant,
        isInvalid: (T) -> Boolean,
        replaceInvalid: (T) -> T,
        distinctByKey: ((T) -> Any)
    ): Pair<List<T>, Double> {
        var correctionCount = 0

        val cleanedList : List<T> = list
        .sortedBy(timeSortKey).distinctBy(distinctByKey)
            .map {
                if (isInvalid(it)) {
                    correctionCount++
                    replaceInvalid(it)
                } else it
            }

        val correctionRatio = if (cleanedList.isNotEmpty()) {
            correctionCount.toDouble() / cleanedList.size
        } else 0.0

        return Pair(cleanedList, correctionRatio)
    }

    val (cleanedHeartRates, correctionHeartRatio) = cleanData(
        list = raw.heartRate,
        timeSortKey = { it.time },
        isInvalid = { it.bpm < 30 || it.bpm > 220 },
        replaceInvalid = { HeartRateEntry(it.time, 0) },
        distinctByKey = { it.time } // entfernt Duplikate basierend auf Start + End
    )

    val (cleanedDistances, correctionDistancesRatio) = cleanData(
        list = raw.distance,
        timeSortKey = { it.end },
        isInvalid = { it.length.inMeters() < 0 },
        replaceInvalid = { DistanceEntry(it.start, it.end, length = Length(0.0)) },
        distinctByKey = { it.start to it.end } // entfernt Duplikate basierend auf Start + End
    )

    // TODO: see other ticket
    return Pair(
        Predictor.MultiTimeSeriesEntries(
            timeStart = raw.timeStart,
            heartRate = cleanedHeartRates,
            distance = cleanedDistances
        ),

        IPreprocessor.QualityRatios(
            cleanedHeartRatesRatio = Percentage(correctionHeartRatio),
            cleanedDistancesRatio = Percentage(correctionDistancesRatio)
        )
    )

}