package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
import kotlin.math.pow
import kotlin.math.sign
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Fills a sparse time series with noise data to prevent collinearity in regression
 * between features/metrics with empty data.
 *
 * If the input [genericTS] has fewer than two data points, this function generates a new
 * time series with random hourly values for the entire duration. This prevents issues
 * like singular matrices in regression models. The random data is seeded by [id] for
 * deterministic, reproducible results.
 *
 * @param id A seed for the random number generator, ensuring consistency.
 * @param genericTS The input time series to check.
 * @return The original time series if it contains sufficient data, or a new one
 *         filled with random data otherwise.
 */
fun ensureData(id: Int, genericTS: GenericTimedDataPointTimeSeries): GenericTimedDataPointTimeSeries {

    if (genericTS.data.size >= 2) {
        return genericTS //TODO: handle case where data exists at near one of the edges, but otherwise
    }

    val random: Random = Random(id)

    val steps = 2//genericTS.duration.inWholeHours.toInt()
    val stepDuration = 1.hours

    val data = List<GenericTimedDataPoint>(steps) { index ->
        GenericTimedDataPoint(
            time = genericTS.timeStart + stepDuration * index,
            value = random.nextDouble(0.0, 1.0)
        )
    }

    return GenericTimedDataPointTimeSeries(
        genericTS.timeStart,
        genericTS.duration,
        genericTS.isContinuous,
        data
    )
}

/**
 * A generic container for a single time series before preprocessing.
 * @property timeStart The starting timestamp of the time series.
 * @property duration The duration of time series.
 * @property data The list of timed data points.
 */
data class GenericTimedDataPointTimeSeries(
    val timeStart: Instant,
    val duration: Duration,
    val isContinuous: Boolean,
    val data: List<GenericTimedDataPoint>,
) {
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
            value = src.bpm.toDouble().let {
                bpm ->
                val limit = 78.0
                val diff = bpm - limit
                diff * diff * diff / 100.0
                /*when {
                    bpm < 60.0 -> bpm - 60.0
                    bpm > 90.0 -> bpm - 90.0
                    else -> 0.0
                }*/
            }
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

        constructor(src: ElevationGainedEntry) : this(
            time = src.end,
            value = src.length.inMeters()
        )

        constructor(src: SkinTemperatureEntry) : this(
            time = src.time,
            value = src.temperature.inCelsius()
        )

        constructor(src: HeartRateVariabilityEntry) : this(
            time = src.time,
            value = src.variability
        )

        constructor(src: OxygenSaturationEntry) : this(
            time = src.time,
            value = src.percentage.toDouble()
        )

        constructor(src: SpeedEntry) : this(
            time = src.end,
            value = src.velocity.inKilometersPerHour()
        )

        constructor(src: StepsEntry) : this(
            time = src.end,
            value = src.count.toDouble()
        )

        constructor(src: SleepSessionEntry) : this(
            time = src.end,
            value = when (src.stage) {
                in listOf(
                    SleepStage.Awake,
                    SleepStage.AwakeInBed,
                    SleepStage.OutOfBed,
                    SleepStage.Unknown
                ) -> 0.0 //awake
                else -> (src.end - src.start) / 1.hours //asleep, count hours
            }
        )

        constructor(src: ValidatedEnergyLevelEntry) : this(
            time = src.time,
            value = src.percentage.toDouble()
        )
    }
}