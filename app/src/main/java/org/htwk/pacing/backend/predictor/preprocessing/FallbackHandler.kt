package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration
import kotlin.time.times
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry

object FallbackHandler {


    /**
     * Ensures that the provided multi-time series data contains entries for all metrics.
     *
     * This function acts as a primary entry point for the data fallback mechanism. If the input `raw` data
     * has an empty list for either heart rate or distance, it delegates to the generic `ensureData` function
     * to fill the gap. The `ensureData` function first attempts to load historical data and, if that fails,
     * generates a default, constant series as a final fallback.
     *
     * @param raw The initial `Predictor.MultiTimeSeriesEntries` which may have empty lists for its series.
     * @return A new `Predictor.MultiTimeSeriesEntries` instance with guaranteed non-empty data for both
     *         heart rate and distance series.
     * @see ensureData
     * @see loadHistoricalHeartRateData
     * @see generateDefaultHeartRateSeries
     * @see loadHistoricalDistanceData
     * @see generateDefaultDistanceSeries
     */
    fun ensureDataFallback(raw: Predictor.MultiTimeSeriesEntries): Predictor.MultiTimeSeriesEntries {
        val heartRate = ensureData (raw.heartRate, raw.timeStart, raw.duration,::loadHistoricalHeartRateData,::generateDefaultHeartRateSeries)
        val distance = ensureData(raw.distance, raw.timeStart, raw.duration,::loadHistoricalDistanceData,::generateDefaultDistanceSeries)
        val elevationGained = ensureData(raw.elevationGained, raw.timeStart, raw.duration,::loadHistoricalElevationGainedData,::generateDefaultElevationGainedSeries)
        val skinTemperature = ensureData(raw.skinTemperature, raw.timeStart, raw.duration,::loadDefaultSkinTemperatureData,::generateDefaultSkinTemperatureSeries)
        val heartRateVariability = ensureData(raw.heartRateVariability, raw.timeStart, raw.duration,::loadDefaultHeartRateVariabilityData,::generateDefaultHeartRateVariabilitySeries)
        val oxygenSaturation = ensureData(raw.oxygenSaturation, raw.timeStart, raw.duration,::loadDefaultOxygenSaturationData,::generateDefaultOxygenSaturationSeries)
        val steps = ensureData(raw.steps, raw.timeStart, raw.duration,::loadDefaultStepsData,::generateDefaultStepsSeries)
        val speed = ensureData(raw.speed, raw.timeStart, raw.duration,::loadDefaultSpeedData,::generateDefaultSpeedSeries)

        return Predictor.MultiTimeSeriesEntries(
            timeStart = raw.timeStart,
            duration = raw.duration,
            heartRate = heartRate,
            distance = distance,
            elevationGained = elevationGained,
            skinTemperature = skinTemperature,
            heartRateVariability = heartRateVariability,
            oxygenSaturation = oxygenSaturation,
            steps = steps,
            speed = speed
        )
    }

    /**
     * A generic data provisioning function that ensures a non-empty list of data is returned.
     *
     * This function implements a three-step fallback strategy:
     * 1.  It first checks if the provided `raw` list is not empty. If so, it returns it directly.
     * 2.  If `raw` is empty, it calls the `historicalDataFunction` to attempt to load historical data.
     *     If historical data is found, it is returned.
     * 3.  If no historical data is available, it calls the `defaultDataFunction` as a last resort to
     *     generate a default dataset. If this is successful, the default data is returned.
     *
     * If all three steps fail to produce a non-empty list, it signifies that no data is available
     * for the requested time period, and an exception is thrown.
     *
     * @param T The type of data in the time series (e.g., `HeartRateEntry`, `DistanceEntry`).
     * @param raw The initial list of data, which might be empty.
     * @param timeStart The start time for which data is required.
     * @param duration The duration for which data is required.
     * @param historicalDataFunction A function that attempts to load historical data for the given time range.
     * @param defaultDataFunction A function that generates a default dataset for the given time range.
     * @return A non-empty `List<T>` containing the data.
     * @throws IllegalStateException if all fallback mechanisms fail and no data can be provided.
     */
    private fun <T> ensureData(
        raw: List<T>,
        timeStart: Instant,
        duration: Duration,
        historicalDataFunction: (Instant, Duration) -> List<T>,
        defaultDataFunction: (Instant, Duration) -> List<T>,
        ): List<T> {
        if (raw.isNotEmpty()) return raw

        val history = historicalDataFunction(timeStart, duration)
        if (history.isNotEmpty()) return history

        val default = defaultDataFunction(timeStart, duration)
        if (default.isNotEmpty()) return default

        throw IllegalStateException("no data available – no prediction possible.")
    }


    /**
     * Attempts to load historical data from the cache.
     *
     * This function is intended to retrieve past heart rate measurements for a specified time range.
     * It serves as a secondary data source when real-time data is unavailable. The current
     * implementation is a placeholder and returns an empty list, because the cache is not implemented yet.
     *
     * @param start The [Instant] marking the beginning of the time range for which to load data.
     * @param duration The [Duration] of the time range.
     * @return A [List] of [HeartRateEntry] objects containing the historical data. Returns an empty
     *         list if no data is found or if the implementation is not yet complete.
     */
    private fun loadHistoricalHeartRateData(start: Instant, duration: Duration): List<HeartRateEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

/**
     * See loadHistoricalHeartRateData
     *
    * @param start The [Instant] marking the beginning of the time range for which to load data.
    * @param duration The [Duration] of the time range.
    * @return A [List] of [DistanceEntry] objects containing the historical data. Returns an empty
    *         list if no data is found or if the implementation is not yet complete.
    */
    private fun loadHistoricalDistanceData(start: Instant, duration: Duration): List<DistanceEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun loadHistoricalElevationGainedData(start: Instant, duration: Duration): List<ElevationGainedEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun loadDefaultSkinTemperatureData(start: Instant, duration: Duration): List<SkinTemperatureEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun loadDefaultHeartRateVariabilityData(start: Instant, duration: Duration): List<HeartRateVariabilityEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun loadDefaultOxygenSaturationData(start: Instant, duration: Duration): List<OxygenSaturationEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun loadDefaultStepsData(start: Instant, duration: Duration): List<StepsEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun loadDefaultSpeedData(start: Instant, duration: Duration): List<SpeedEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }



    /**
     * Generates a default, constant heart rate time series if no other data is available.
     *
     * This function serves as a last-resort fallback. It creates a list of [HeartRateEntry]
     * objects with a fixed BPM value (currently 75L) for the entire specified duration. The time
     * series is generated at regular intervals defined by [Predictor.TIME_SERIES_STEP_DURATION].
     *
     * @param start The [Instant] at which the time series should begin.
     * @param duration The total [Duration] for which the time series should be generated.
     * @return A [List] of [HeartRateEntry] objects representing the default heart rate data.
     */

    //The daily curve should be applied using a neutral average over the time period to generate more realistic values
    //defaultBpm needs to be dynamic
    private fun generateDefaultHeartRateSeries(start: Instant, duration: Duration): List<HeartRateEntry> {
        val step = Predictor.TIME_SERIES_STEP_DURATION
        val points = (duration / step).toInt()
        val defaultBpm = 75L
        return List(points) { i -> HeartRateEntry(start + (i * step), defaultBpm) }
    }

    /**
     * Generates a default time series for distance data as a fallback.
     *
     * This function is used when no actual or historical distance data is available. It creates a list
     * of [DistanceEntry] objects, each representing a time step of a fixed duration
     * ([Predictor.TIME_SERIES_STEP_DURATION]). For each step, the distance covered is set to a default
     * value of 0.0, effectively representing a stationary state.
     *
     * @param start The [Instant] when the time series begins.
     * @param duration The total [Duration] of the time series to generate.
     * @return A [List] of [DistanceEntry] objects with a distance of 0.0 for each time step.
     */

    //default length could be changed
    private fun generateDefaultDistanceSeries(start: Instant, duration: Duration): List<DistanceEntry> {
        val step = Predictor.TIME_SERIES_STEP_DURATION
        val points = (duration / step).toInt()
        val defaultLength = 8.0
        return List(points) { i ->
            DistanceEntry(
                start + (i * step),
                start + ((i + 1) * step),
                Length(defaultLength)
            )
        }
    }

    private fun generateDefaultElevationGainedSeries(start: Instant, duration: Duration): List<ElevationGainedEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun generateDefaultSkinTemperatureSeries(start: Instant, duration: Duration): List<SkinTemperatureEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun generateDefaultHeartRateVariabilitySeries(start: Instant, duration: Duration): List<HeartRateVariabilityEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun generateDefaultOxygenSaturationSeries(start: Instant, duration: Duration): List<OxygenSaturationEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun generateDefaultStepsSeries(start: Instant, duration: Duration): List<StepsEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

    private fun generateDefaultSpeedSeries(start: Instant, duration: Duration): List<SpeedEntry> {
        // TODO: Depends on cache implementation
        return emptyList()
    }

}