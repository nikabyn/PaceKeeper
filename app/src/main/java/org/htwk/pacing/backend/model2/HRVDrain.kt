package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.sqrt

object HRVDrain {
    /**
     * calculates HRV (RMSSD-Approximation) from HR-data.
     * NoTICE: only Approximation, one second from hr-data is too long for real calculation
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

            val hrValues = windowData.map { it.bpm }
            var sumSquaredDiffs = 0.0
            for (j in 1 until hrValues.size) {
                val diff = hrValues[j] - hrValues[j - 1]
                sumSquaredDiffs += diff * diff
            }
            val rmssd = sqrt(sumSquaredDiffs / (hrValues.size - 1))

            //Plausibility check
            if (rmssd in 0.0..<50.0) {
                results.add(HRVPoint(timestamp = sorted[i].timestamp, rmssd = rmssd))
            }
        }

        return results
    }

    /**
     * calculates HRV-Baseline (Median RMSSD) for Comparison
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

    fun getHRVAtTime(
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
     * calculates Drain-Multiplier from actual HRV vs Baseline.
     *
     * - ratio < lowThreshold (0.7):  1.3x Drain
     * - ratio > highThreshold (1.3): 0.8x Drain
     * - between:                  Normal → 1.0x Drain
     */
    fun getDrainMultiplier(
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

    private data class Anchor(val timestamp: Instant, val energy: Double)

    private fun buildAnchorPoints(
        sortedValidated: List<EnergyDataPoint>,
        hrStart: Long,
        hrEnd: Long,
        offsetMs: Long,
        fallbackStartEnergy: Double,
        firstHRTimestamp: Instant
    ): List<Anchor> {
        val anchors = mutableListOf<Anchor>()

        // first Anchor: last validated user input
        val beforeStart = sortedValidated.filter { v ->
            v.timestamp.toEpochMilliseconds() - offsetMs <= hrStart
        }
        if (beforeStart.isNotEmpty()) {
            anchors.add(Anchor(firstHRTimestamp, beforeStart.last().percentage))
        } else {
            anchors.add(Anchor(firstHRTimestamp, fallbackStartEnergy))
        }

        // more Anchors: more validated user input in between HR-area
        sortedValidated.forEach { v ->
            val adjustedTime = v.timestamp.toEpochMilliseconds() - offsetMs
            if (adjustedTime > hrStart && adjustedTime <= hrEnd) {
                anchors.add(Anchor(Instant.fromEpochMilliseconds(adjustedTime), v.percentage))
            }
        }

        return anchors
    }

    private fun <T> filterDataForSegment(
        data: List<T>,
        anchor: Anchor,
        nextAnchor: Anchor?,
        getTimestamp: (T) -> Instant
    ): List<T> {
        return data.filter { item ->
            val t = getTimestamp(item).toEpochMilliseconds()
            if (nextAnchor != null) {
                t >= anchor.timestamp.toEpochMilliseconds() && t < nextAnchor.timestamp.toEpochMilliseconds()
            } else {
                t >= anchor.timestamp.toEpochMilliseconds()
            }
        }
    }
}