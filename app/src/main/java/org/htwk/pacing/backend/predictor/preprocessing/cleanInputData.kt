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
        distinctByKey: ((T) -> Any)
    ): Pair<List<T>, Double> {
        val cleanedList : List<T> = list
        .sortedBy(timeSortKey).distinctBy(distinctByKey).filterNot { isInvalid(it) }

        val acceptedValuesRatio: Double = if (list.isNotEmpty()) {
            cleanedList.size.toDouble() / list.size.toDouble()
        } else 1.0

        return Pair(cleanedList, acceptedValuesRatio)
    }

    val (cleanedHeartRates, correctionHeartRatio) = cleanData(
        list = raw.heartRate,
        timeSortKey = { it.time },
        isInvalid = { it.bpm < 30 || it.bpm > 220 },
        distinctByKey = { it.time } // entfernt Duplikate basierend auf Start + End
    )

    val (cleanedDistances, correctionDistancesRatio) = cleanData(
        list = raw.distance,
        timeSortKey = { it.end },
        isInvalid = { it.length.inMeters() < 0 },
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