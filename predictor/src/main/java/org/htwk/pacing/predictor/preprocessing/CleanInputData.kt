package org.htwk.pacing.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.predictor.Predictor
import org.koin.core.time.inMs

/**
 * Holds data quality metrics calculated during preprocessing.
 * @property ratiosPerMetric A per-metric map of remaining data points remaining after cleaning.
 */
data class QualityRatios(
    val ratiosPerMetric: Map<TimeSeriesMetric, Percentage>
)

//TODO: add pattern-matching-based invalid data sanitization, so that for different kinds of errors we can respond in different ways
fun cleanInputData(raw: Predictor.MultiTimeSeriesEntries): Pair<Predictor.MultiTimeSeriesEntries, QualityRatios> {
    val MAX_VALID_SPEED_MPS = 20.0 //movement speed, walking (m/s)
    val MAX_VALID_ELEVATION_CHANGE_MPS = 2.0 //max. accepted elevation change (m/s)
    val MAX_VALID_STEPS_PER_SECOND = 4.0 //max. accepted steps per second
    val VALID_SKIN_TEMPERATURE_RANGE_CELSIUS = 25.0..42.0; //allowed temperature range in degrees celsius
    val MAX_VALID_SPEED_KPH = 500.0 //max. accepted movement speed
    val VALID_OXYGEN_SATURATION_RANGE = 70.0..100.0
    val VALID_HEART_RATE_RANGE = 30..220

    fun continuousRateOfChange(
        start: Instant,
        end: Instant,
        quantity: Double,
    ): Double {
        val deltaSeconds: Double = (end - start).inMs / 1000.0
        if (deltaSeconds <= 0.0) return Double.NaN
        return quantity / deltaSeconds
    }

    /**
     * Cleans a generic list of time series data by sorting, removing duplicates, and filtering invalid entries.
     * Calculates the ratio of valid (kept) entries to the original number.
     * @param T The generic type of the elements in the list.
     * @param list The raw list of data points to be cleaned.
     * @param timeSortKey A function that extracts an [kotlinx.datetime.Instant] from an element for sorting.
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
        isInvalid = { it.bpm !in VALID_HEART_RATE_RANGE }, //filter out if bpm outside sensible range
        distinctByKey = { it.time } //uniqueness based on timestamp
    )

    val (cleanedDistances, correctionDistancesRatio) = cleanData(
        list = raw.distance,
        timeSortKey = { it.end },
        isInvalid = {
            val changeRate = continuousRateOfChange(
                it.start,
                it.end,
                it.length.inMeters()
            )
            changeRate !in 0.0..MAX_VALID_SPEED_MPS
        },
        distinctByKey = { it.start to it.end }
    )

    val (cleanedElevationGains, correctionElevationGainsRatio) = cleanData(
        list = raw.elevationGained,
        timeSortKey = { it.end },
        isInvalid = {
            val changeRate = continuousRateOfChange(
                it.start,
                it.end,
                it.length.inMeters()
            )
            changeRate !in 0.0..MAX_VALID_ELEVATION_CHANGE_MPS
        },
        distinctByKey = { it.start to it.end }
    )

    val (cleanedSkinTemperatures, correctionSkinTemperaturesRatio) = cleanData(
        list = raw.skinTemperature,
        timeSortKey = { it.time },
        isInvalid = { it.temperature.inCelsius() !in VALID_SKIN_TEMPERATURE_RANGE_CELSIUS},
        distinctByKey = { it.time }
    )

    val (cleanedHeartRateVariabilities, correctionHeartRateVariabilitiesRatio) = cleanData(
        list = raw.heartRateVariability,
        timeSortKey = { it.time },
        isInvalid = { it.variability < 0 },
        distinctByKey = { it.time }
    )

    val (cleanedOxygenSaturations, correctionOxygenSaturationsRatio) = cleanData(
        list = raw.oxygenSaturation,
        timeSortKey = { it.time },
        isInvalid = { it.percentage.toDouble() !in VALID_OXYGEN_SATURATION_RANGE },
        distinctByKey = { it.time }
    )

    val (cleanedSteps, correctionStepsRatio) = cleanData(
        list = raw.steps,
        timeSortKey = { it.end },
        isInvalid = {
            if (it.count == 0L) return@cleanData true
            val deltaSeconds = (it.end - it.start).inMs / 1000.0
            if (deltaSeconds <= 0.0) return@cleanData true
            val stepsPerSecond = it.count.toDouble() / deltaSeconds
            return@cleanData stepsPerSecond !in 0.0..MAX_VALID_STEPS_PER_SECOND
        },
        distinctByKey = { it.start to it.end }
    )

    val (cleanedSpeeds, correctionSpeedsRatio) = cleanData(
        list = raw.speed,
        timeSortKey = { it.end },
        isInvalid = { it.velocity.inKilometersPerHour() !in 0.0..MAX_VALID_SPEED_KPH},
        distinctByKey = { it.start to it.end }
    )

    return Pair(
        Predictor.MultiTimeSeriesEntries(
            timeStart = raw.timeStart,
            duration = raw.duration,
            heartRate = cleanedHeartRates,
            distance = cleanedDistances,
            elevationGained = cleanedElevationGains,
            skinTemperature = cleanedSkinTemperatures,
            heartRateVariability = cleanedHeartRateVariabilities,
            oxygenSaturation = cleanedOxygenSaturations,
            steps = cleanedSteps,
            speed = cleanedSpeeds,
            sleepSession = raw.sleepSession, //don't clean sleep for now
        ),

        QualityRatios(
            ratiosPerMetric = TimeSeriesMetric.entries.associateWith { metric ->
                when (metric) {
                    TimeSeriesMetric.HEART_RATE -> Percentage(correctionHeartRatio)
                    TimeSeriesMetric.DISTANCE -> Percentage(correctionDistancesRatio)
                    TimeSeriesMetric.ELEVATION_GAINED -> Percentage(correctionElevationGainsRatio)
                    TimeSeriesMetric.SKIN_TEMPERATURE -> Percentage(correctionSkinTemperaturesRatio)
                    TimeSeriesMetric.HEART_RATE_VARIABILITY -> Percentage(
                        correctionHeartRateVariabilitiesRatio
                    )

                    TimeSeriesMetric.OXYGEN_SATURATION -> Percentage(
                        correctionOxygenSaturationsRatio
                    )

                    TimeSeriesMetric.STEPS -> Percentage(correctionStepsRatio)
                    TimeSeriesMetric.SPEED -> Percentage(correctionSpeedsRatio)
                    TimeSeriesMetric.SLEEP_SESSION -> Percentage(1.0)
                }
            }

        )
    )
}