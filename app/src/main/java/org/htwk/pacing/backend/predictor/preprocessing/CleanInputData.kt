package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.predictor.Predictor

//TODO: add pattern-matching-based invalid data sanitization, so that for different kinds of errors we can respond in different ways
fun cleanInputData(raw: Predictor.MultiTimeSeriesEntries): Pair<Predictor.MultiTimeSeriesEntries, IPreprocessor.QualityRatios> {
    /**
     * Cleans a generic list of time series data by sorting, removing duplicates, and filtering invalid entries.
     * Calculates the ratio of valid (kept) entries to the original number.
     * @param T The generic type of the elements in the list.
     * @param list The raw list of data points to be cleaned.
     * @param timeSortKey A function that extracts an [Instant] from an element for sorting.
     * @param isInvalid A predicate function that returns `true` if an element is invalid and should be removed.
     * @param distinctByKey A function that extracts a key from an element to identify duplicates.
     * @return A [Pair] containing the cleaned and sorted list, and a [Double] representing the ratio of accepted values (from 0.0 to 1.0).
     */
    fun <T> cleanData(
        list: List<T>,
        timeSortKey: (T) -> Instant,
        isInvalid: (T) -> Boolean,
        distinctByKey: ((T) -> Any)
    ): Pair<List<T>, Double> {
        val cleanedList: List<T> = list
            .sortedBy(timeSortKey).distinctBy(distinctByKey).filterNot { isInvalid(it) }

        val acceptedValuesRatio: Double = if (list.isNotEmpty()) {
            cleanedList.size.toDouble() / list.size.toDouble()
        } else 1.0

        return Pair(cleanedList, acceptedValuesRatio)
    }

    val (cleanedHeartRates, correctionHeartRatio) = cleanData(
        list = raw.heartRate,
        timeSortKey = { it.time },
        isInvalid = { it.bpm < 30 || it.bpm > 220 }, //filter out if bpm outside sensible range
        distinctByKey = { it.time } //uniqueness based on timestamp
    )

    val (cleanedDistances, correctionDistancesRatio) = cleanData(
        list = raw.distance,
        timeSortKey = { it.end },
        isInvalid = { it.length.inMeters() <= 0 }, //filter out negative distance entries
        distinctByKey = { it.start to it.end } //if same end and start time, we treat as duplicate
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