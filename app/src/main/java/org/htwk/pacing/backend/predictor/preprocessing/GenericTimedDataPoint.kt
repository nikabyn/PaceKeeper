package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.koin.core.time.inMs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * A generic container for a single time series before preprocessing.
 * @property timeStart The starting timestamp of the time series.
 * @property duration The duration of time series.
 * @property metric The metric the time series is generated from.
 * @property data The list of timed data points.
 */
data class GenericTimedDataPointTimeSeries(
    val timeStart: Instant,
    val duration: Duration,
    val metric: TimeSeriesMetric,
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
            value = when(src.stage) {
                in listOf(SleepStage.Awake, SleepStage.AwakeInBed, SleepStage.OutOfBed, SleepStage.Unknown) -> 0.0 //awake
                else -> (src.end - src.start).inMs / 1.hours.inMs //asleep
            }
        )

        constructor(src: ValidatedEnergyLevelEntry) : this(
            time = src.time,
            value = src.percentage.toDouble()
        )
    }
}