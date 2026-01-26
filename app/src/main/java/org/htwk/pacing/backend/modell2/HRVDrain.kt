package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * HRV-based energy drain calculation
 */
object HRVDrain {

    val DEFAULT_HRV_DRAIN_CONFIG = HRVDrainConfig()

    /**
     * Calculates RMSSD from RR intervals.
     */
    private fun calculateRMSSD(rrIntervals: List<Double>): Double {
        if (rrIntervals.size < 2) return 0.0

        var sumSquaredDiffs = 0.0
        for (i in 1 until rrIntervals.size) {
            val diff = rrIntervals[i] - rrIntervals[i - 1]
            sumSquaredDiffs += diff * diff
        }

        return sqrt(sumSquaredDiffs / (rrIntervals.size - 1))
    }

    /**
     * Calculates HRV (RMSSD) from heart rate data using HR differences.
     * Note: This is an approximation since we don't have actual RR intervals.
     */
    fun calculateHRVFromHR(
        hrData: List<HRDataPoint>,
        windowMinutes: Int = 5
    ): List<HRVPoint> {
        if (hrData.size < 5) return emptyList()

        val sorted = hrData.sortedBy { it.timestamp }
        val results = mutableListOf<HRVPoint>()
        val windowMs = windowMinutes * 60 * 1000L

        for (i in sorted.indices) {
            val windowEnd = sorted[i].timestamp.toEpochMilliseconds()
            val windowStart = windowEnd - windowMs

            val windowData = sorted.filter { d ->
                val ts = d.timestamp.toEpochMilliseconds()
                ts > windowStart && ts <= windowEnd
            }

            if (windowData.size < 3) continue

            // Calculate RMSSD from HR differences (not RR intervals)
            val hrValues = windowData.map { it.bpm }
            var sumSquaredDiffs = 0.0
            for (j in 1 until hrValues.size) {
                val diff = hrValues[j] - hrValues[j - 1]
                sumSquaredDiffs += diff * diff
            }
            val rmssd = sqrt(sumSquaredDiffs / (hrValues.size - 1))

            if (rmssd >= 0 && rmssd < 50) {
                results.add(
                    HRVPoint(
                        timestamp = sorted[i].timestamp,
                        rmssd = rmssd
                    )
                )
            }
        }

        return results
    }

    /**
     * Calculates HRV baseline (median RMSSD).
     */
    fun calculateHRVBaseline(hrvData: List<HRVPoint>): Double {
        if (hrvData.isEmpty()) return 50.0

        val values = hrvData.map { it.rmssd }.sorted()
        val mid = values.size / 2

        return if (values.size % 2 == 0) {
            (values[mid - 1] + values[mid]) / 2.0
        } else {
            values[mid]
        }
    }

    /**
     * Gets the HRV value closest to a target time.
     * Returns null if no value within maxDiffMs.
     */
    private fun getHRVAtTime(
        hrvData: List<HRVPoint>,
        targetTime: Instant,
        maxDiffMs: Long = 5 * 60 * 1000L
    ): Double? {
        if (hrvData.isEmpty()) return null

        val t = targetTime.toEpochMilliseconds()
        var closest = hrvData[0]
        var minDiff = abs(hrvData[0].timestamp.toEpochMilliseconds() - t)

        for (point in hrvData) {
            val diff = abs(point.timestamp.toEpochMilliseconds() - t)
            if (diff < minDiff) {
                minDiff = diff
                closest = point
            }
        }

        return if (minDiff <= maxDiffMs) closest.rmssd else null
    }

    /**
     * Gets the drain multiplier based on current HRV vs baseline.
     */
    private fun getDrainMultiplier(
        currentHRV: Double?,
        baseline: Double,
        config: HRVDrainConfig
    ): Double {
        if (currentHRV == null) return config.normalHRVMultiplier

        val ratio = currentHRV / baseline

        return when {
            ratio < config.lowThreshold -> config.lowHRVMultiplier
            ratio > config.highThreshold -> config.highHRVMultiplier
            else -> config.normalHRVMultiplier
        }
    }

    /**
     * Calculates energy with HRV-based drain modification.
     */
    fun calculateEnergyWithHRVDrain(
        hrAgg: List<HRDataPoint>,
        hrvData: List<HRVPoint>,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        timeOffsetMinutes: Int,
        aggregationMinutes: Int,
        wakeEvents: List<WakeEvent>,
        resetOnWake: Boolean,
        startEnergy: Double,
        energyOffset: Double,
        hrvConfig: HRVDrainConfig = DEFAULT_HRV_DRAIN_CONFIG
    ): List<EnergyResultWithHRV> {
        if (hrAgg.isEmpty()) return emptyList()

        val result = mutableListOf<EnergyResultWithHRV>()
        var energy = startEnergy
        val offsetMs = timeOffsetMinutes * 60 * 1000L
        val wakeTimestamps = wakeEvents.map { it.timestamp.toEpochMilliseconds() }.toSet()

        val baseline = calculateHRVBaseline(hrvData)

        for (i in hrAgg.indices) {
            val hr = hrAgg[i].bpm
            val ts = hrAgg[i].timestamp
            val tsMs = ts.toEpochMilliseconds()

            val deltaMinutes = if (i > 0) {
                (tsMs - hrAgg[i - 1].timestamp.toEpochMilliseconds()) / 60000.0
            } else {
                aggregationMinutes.toDouble()
            }

            val currentHRV = getHRVAtTime(hrvData, ts)
            val hrvMultiplier = getDrainMultiplier(currentHRV, baseline, hrvConfig)

            if (resetOnWake && wakeTimestamps.contains(tsMs)) {
                energy = 100.0
            } else {
                if (hr < hrLow) {
                    energy += (hrLow - hr) * 0.1 * recoveryFactor * (deltaMinutes / 15.0)
                } else if (hr > hrHigh) {
                    energy -= (hr - hrHigh) * 0.15 * drainFactor * hrvMultiplier * (deltaMinutes / 15.0)
                }
                energy = max(0.0, min(100.0, energy))
            }

            result.add(
                EnergyResultWithHRV(
                    timestamp = Instant.fromEpochMilliseconds(tsMs + offsetMs),
                    energy = max(0.0, min(100.0, energy - energyOffset)),
                    hrvMultiplier = hrvMultiplier
                )
            )
        }

        return result
    }

    /**
     * Calculates energy with HRV drain and anchor points to validated energy.
     */
    fun calculateEnergyWithHRVDrainAnchored(
        hrAgg: List<HRDataPoint>,
        hrvData: List<HRVPoint>,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        timeOffsetMinutes: Int,
        aggregationMinutes: Int,
        wakeEvents: List<WakeEvent>,
        resetOnWake: Boolean,
        validatedPoints: List<EnergyDataPoint>,
        fallbackStartEnergy: Double,
        energyOffset: Double,
        hrvConfig: HRVDrainConfig = DEFAULT_HRV_DRAIN_CONFIG
    ): List<EnergyResultWithHRV> {
        if (hrAgg.isEmpty()) return emptyList()

        val sortedValidated = validatedPoints.sortedBy { it.timestamp }
        val offsetMs = timeOffsetMinutes * 60 * 1000L
        val hrStart = hrAgg.first().timestamp.toEpochMilliseconds()
        val hrEnd = hrAgg.last().timestamp.toEpochMilliseconds()

        // Build anchor points
        data class Anchor(val timestamp: Instant, val energy: Double)
        val anchors = mutableListOf<Anchor>()

        // Find first anchor (last validated point before HR start + offset)
        val beforeStart = sortedValidated.filter { v ->
            v.timestamp.toEpochMilliseconds() - offsetMs <= hrStart
        }
        if (beforeStart.isNotEmpty()) {
            anchors.add(Anchor(hrAgg.first().timestamp, beforeStart.last().percentage))
        } else {
            anchors.add(Anchor(hrAgg.first().timestamp, fallbackStartEnergy))
        }

        // Add validated points within HR range (with offset correction)
        sortedValidated.forEach { v ->
            val adjustedTime = v.timestamp.toEpochMilliseconds() - offsetMs
            if (adjustedTime > hrStart && adjustedTime <= hrEnd) {
                anchors.add(Anchor(Instant.fromEpochMilliseconds(adjustedTime), v.percentage))
            }
        }

        val result = mutableListOf<EnergyResultWithHRV>()
        val baseline = calculateHRVBaseline(hrvData)

        // Calculate energy for each segment between anchors
        for (i in anchors.indices) {
            val anchor = anchors[i]
            val nextAnchor = anchors.getOrNull(i + 1)

            val segmentHR = hrAgg.filter { hr ->
                val t = hr.timestamp.toEpochMilliseconds()
                if (nextAnchor != null) {
                    t >= anchor.timestamp.toEpochMilliseconds() && t < nextAnchor.timestamp.toEpochMilliseconds()
                } else {
                    t >= anchor.timestamp.toEpochMilliseconds()
                }
            }

            if (segmentHR.isEmpty()) continue

            val segmentHRV = hrvData.filter { h ->
                val t = h.timestamp.toEpochMilliseconds()
                if (nextAnchor != null) {
                    t >= anchor.timestamp.toEpochMilliseconds() && t < nextAnchor.timestamp.toEpochMilliseconds()
                } else {
                    t >= anchor.timestamp.toEpochMilliseconds()
                }
            }

            val segmentWakeEvents = wakeEvents.filter { w ->
                val t = w.timestamp.toEpochMilliseconds()
                if (nextAnchor != null) {
                    t >= anchor.timestamp.toEpochMilliseconds() && t < nextAnchor.timestamp.toEpochMilliseconds()
                } else {
                    t >= anchor.timestamp.toEpochMilliseconds()
                }
            }

            val segmentResult = calculateEnergyWithHRVDrain(
                hrAgg = segmentHR,
                hrvData = segmentHRV,
                hrLow = hrLow,
                hrHigh = hrHigh,
                drainFactor = drainFactor,
                recoveryFactor = recoveryFactor,
                timeOffsetMinutes = timeOffsetMinutes,
                aggregationMinutes = aggregationMinutes,
                wakeEvents = segmentWakeEvents,
                resetOnWake = resetOnWake,
                startEnergy = anchor.energy,
                energyOffset = energyOffset,
                hrvConfig = hrvConfig
            )

            result.addAll(segmentResult)
        }

        return result
    }
}
