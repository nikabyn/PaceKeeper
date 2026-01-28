package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min

/**
 * Energy calculation logic
 */
object EnergyCalculation {

    // 2 hours delay in milliseconds
    const val HR_DELAY_MS = 2 * 60 * 60 * 1000L

    /**
     * Calculates energy levels based on heart rate data.
     *
     * @param hrAgg Aggregated heart rate data points
     * @param hrLow Lower HR threshold (recovery zone)
     * @param hrHigh Upper HR threshold (drain zone)
     * @param drainFactor Factor for energy drain when HR > hrHigh
     * @param recoveryFactor Factor for energy recovery when HR < hrLow
     * @param timeOffsetMinutes Time offset to apply to timestamps (in minutes)
     * @param aggregationMinutes Time bucket size for aggregation (in minutes)
     * @param startEnergy Starting energy level
     * @param energyOffset Offset to subtract from final energy
     */
    fun calculateEnergyWithParams(
        hrAgg: List<HRDataPoint>,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        timeOffsetMinutes: Int,
        aggregationMinutes: Int,
        startEnergy: Double,
        energyOffset: Double = 0.0
    ): List<EnergyResult> {
        if (hrAgg.isEmpty()) return emptyList()

        val result = mutableListOf<EnergyResult>()
        var energy = startEnergy
        val offsetMs = timeOffsetMinutes * 60 * 1000L

        for (i in hrAgg.indices) {
            val hr = hrAgg[i].bpm
            val ts = hrAgg[i].timestamp
            val tsMs = ts.toEpochMilliseconds()

            val deltaMinutes = if (i > 0) {
                (tsMs - hrAgg[i - 1].timestamp.toEpochMilliseconds()) / 60000.0
            } else {
                aggregationMinutes.toDouble()
            }


            if (hr < hrLow) {
                energy += (hrLow - hr) * 0.1 * recoveryFactor * (deltaMinutes / 15.0)
            } else if (hr > hrHigh) {
                energy -= (hr - hrHigh) * 0.15 * drainFactor * (deltaMinutes / 15.0)
            }
            energy = max(0.0, min(100.0, energy))


            result.add(
                EnergyResult(
                    timestamp = Instant.fromEpochMilliseconds(tsMs + offsetMs),
                    energy = max(0.0, min(100.0, energy - energyOffset))
                )
            )
        }

        return result
    }

    /**
     * Simulates energy without time offset (used for optimization).
     * Returns a map of timestamp (with HR_DELAY) to energy value.
     */
    fun simulateEnergy(
        hrData: List<HRDataPoint>,
        startEnergy: Double,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        aggregationMinutes: Int
    ): Map<Long, Double> {
        val result = mutableMapOf<Long, Double>()
        var energy = startEnergy

        for (i in hrData.indices) {
            val hr = hrData[i].bpm
            val ts = hrData[i].timestamp.toEpochMilliseconds()

            val deltaMinutes = if (i > 0) {
                (ts - hrData[i - 1].timestamp.toEpochMilliseconds()) / 60000.0
            } else {
                aggregationMinutes.toDouble()
            }

            if (hr < hrLow) {
                energy += (hrLow - hr) * 0.1 * recoveryFactor * (deltaMinutes / 15.0)
            } else if (hr > hrHigh) {
                energy -= (hr - hrHigh) * 0.15 * drainFactor * (deltaMinutes / 15.0)
            }
            energy = max(0.0, min(100.0, energy))

            result[ts + HR_DELAY_MS] = energy
        }

        return result
    }

    /**
     * Finds the closest energy value for a given time.
     * Returns null if no value within 30 minutes.
     */
    fun findClosestEnergy(
        energyMap: Map<Long, Double>,
        targetTime: Long
    ): Double? {
        var closest: Double? = null
        var minDiff = Long.MAX_VALUE

        for ((ts, energy) in energyMap) {
            val diff = kotlin.math.abs(ts - targetTime)
            if (diff < minDiff) {
                minDiff = diff
                closest = energy
            }
        }

        // If more than 30 minutes away, return null
        return if (minDiff > 30 * 60 * 1000) null else closest
    }

    /**
     * Aggregates heart rate data into time buckets.
     */
    fun aggregateHR(
        data: List<HRDataPoint>,
        minutes: Int
    ): List<HRDataPoint> {
        if (data.isEmpty()) return emptyList()

        val buckets = mutableMapOf<Long, MutableList<Double>>()
        val ms = minutes * 60 * 1000L

        for (d in data) {
            val key = (d.timestamp.toEpochMilliseconds() / ms) * ms
            buckets.getOrPut(key) { mutableListOf() }.add(d.bpm)
        }

        return buckets.entries
            .map { (ts, vals) ->
                HRDataPoint(
                    timestamp = Instant.fromEpochMilliseconds(ts),
                    bpm = vals.average()
                )
            }
            .sortedBy { it.timestamp }
    }
}
