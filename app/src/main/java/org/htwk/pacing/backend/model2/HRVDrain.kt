package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * HRV-basierte Energieberechnung mit Anchoring.
 *
 * ARCHITEKTUR:
 * ============
 *
 * HAUPT-ENTRY-POINT:
 *   calculateEnergyWithHRVDrainAnchored()
 *     - Segmentiert HR-Daten anhand von Anchor-Punkten (validierte Energie-Eingaben)
 *     - Ruft für jedes Segment calculateEnergyWithHRVDrain() auf
 *     - Springt bei jedem Anchor zum validierten Wert
 *
 * ENERGIE-FORMEL (pro Zeitschritt):
 *   wenn HR < hrLow:  energy += (hrLow - HR) * 0.1 * recoveryFactor * (deltaMin/15)
 *   wenn HR > hrHigh: energy -= (HR - hrHigh) * 0.15 * drainFactor * hrvMultiplier * (deltaMin/15)
 *
 * HRV-MULTIPLIER:
 *   - Niedriger HRV (Stress) → höherer Drain (1.3x)
 *   - Normaler HRV → normaler Drain (1.0x)
 *   - Hoher HRV (Entspannung) → niedrigerer Drain (0.8x)
 *
 * ANCHORING:
 *   - Nutzer-validierte Energiewerte überschreiben berechnete Werte
 *   - Berechnung startet vom letzten Anchor neu
 *   - Verhindert Drift über lange Zeiträume
 *
 * CALL-FLOW:
 *   Predictor_modell2.predict()
 *     → HeartRatePredictionModel_modell2.predict()
 *       → calculateEnergyWithHRVDrainAnchored()  ← ENTRY POINT
 *         → calculateEnergyWithHRVDrain()        ← pro Segment
 */
object HRVDrain {

    val DEFAULT_HRV_DRAIN_CONFIG = HRVDrainConfig()

    // ========================================================================
    // HAUPT-ENTRY-POINT
    // ========================================================================

    /**
     * calculates energy with HRV-Drain and Anchoring from validated user inputs
     *
     * ZENTRAL METHOD for energy prediction
     *
     * @param hrAgg Aggregierte HR-Daten (EnergyCalculation.aggregateHR)
     * @param hrvData HRV-Daten (calculateHRVFromHR)
     * @param hrLow
     * @param hrHigh
     * @param drainFactor
     * @param recoveryFactor
     * @param timeOffsetMinutes
     * @param aggregationMinutes
     * @param validatedPoints Nutzer-validierte Energie-Eingaben (ANCHOR POINTS)
     * @param fallbackStartEnergy Startwert wenn kein Anchor vor HR-Start existiert
     * @param energyOffset Trainierter Offset (wird von Energie subtrahiert)
     * @param hrvConfig Konfiguration für HRV-Multiplier-Schwellen
     *
     * @return Liste von Energie-Ergebnissen mit Zeitstempel und HRV-Multiplier
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

        // Anchor-Punkte aufbauen
        val anchors = buildAnchorPoints(
            sortedValidated = sortedValidated,
            hrStart = hrStart,
            hrEnd = hrEnd,
            offsetMs = offsetMs,
            fallbackStartEnergy = fallbackStartEnergy,
            firstHRTimestamp = hrAgg.first().timestamp
        )

        // Energie pro Segment berechnen
        val result = mutableListOf<EnergyResultWithHRV>()

        for (i in anchors.indices) {
            val anchor = anchors[i]
            val nextAnchor = anchors.getOrNull(i + 1)

            val segmentHR = filterDataForSegment(hrAgg, anchor, nextAnchor) { it.timestamp }
            if (segmentHR.isEmpty()) continue

            val segmentHRV = filterDataForSegment(hrvData, anchor, nextAnchor) { it.timestamp }

            val segmentResult = calculateEnergyWithHRVDrain(
                hrAgg = segmentHR,
                hrvData = segmentHRV,
                hrLow = hrLow,
                hrHigh = hrHigh,
                drainFactor = drainFactor,
                recoveryFactor = recoveryFactor,
                timeOffsetMinutes = timeOffsetMinutes,
                aggregationMinutes = aggregationMinutes,
                startEnergy = anchor.energy,
                energyOffset = energyOffset,
                hrvConfig = hrvConfig
            )

            result.addAll(segmentResult)
        }

        return result
    }

    // ========================================================================
    // SEGMENT-BERECHNUNG (intern)
    // ========================================================================

    /**
     * Berechnet Energie für ein einzelnes Segment zwischen zwei Anchors.
     *
     * NICHT DIREKT AUFRUFEN - wird von calculateEnergyWithHRVDrainAnchored verwendet.
     *
     * Energie-Formel pro Zeitschritt:
     * - HR < hrLow:  energy += (hrLow - HR) * 0.1 * recoveryFactor * (deltaMin/15)
     * - HR > hrHigh: energy -= (HR - hrHigh) * 0.15 * drainFactor * hrvMultiplier * (deltaMin/15)
     */
    internal fun calculateEnergyWithHRVDrain(
        hrAgg: List<HRDataPoint>,
        hrvData: List<HRVPoint>,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        timeOffsetMinutes: Int,
        aggregationMinutes: Int,
        startEnergy: Double,
        energyOffset: Double,
        hrvConfig: HRVDrainConfig = DEFAULT_HRV_DRAIN_CONFIG
    ): List<EnergyResultWithHRV> {
        if (hrAgg.isEmpty()) return emptyList()

        val result = mutableListOf<EnergyResultWithHRV>()
        var energy = startEnergy
        val offsetMs = timeOffsetMinutes * 60 * 1000L
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

            energy = calculateEnergyChange(
                currentEnergy = energy,
                hr = hr,
                hrLow = hrLow,
                hrHigh = hrHigh,
                drainFactor = drainFactor,
                recoveryFactor = recoveryFactor,
                hrvMultiplier = hrvMultiplier,
                deltaMinutes = deltaMinutes
            )

            result.add(
                EnergyResultWithHRV(
                    timestamp = Instant.fromEpochMilliseconds(tsMs + offsetMs),
                    energy = (energy - energyOffset).coerceIn(0.0, 100.0),
                    hrvMultiplier = hrvMultiplier
                )
            )
        }

        return result
    }

    // ========================================================================
    // ENERGIE-FORMEL
    // ========================================================================

    /**
     * Berechnet Energie-Änderung für einen Zeitschritt.
     *
     * Formel:
     * - Recovery (HR < hrLow):  +0.1 * (hrLow - HR) * recoveryFactor * (delta/15)
     * - Drain (HR > hrHigh):    -0.15 * (HR - hrHigh) * drainFactor * hrvMultiplier * (delta/15)
     * - Neutral (dazwischen):   keine Änderung
     */
    private fun calculateEnergyChange(
        currentEnergy: Double,
        hr: Double,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        hrvMultiplier: Double,
        deltaMinutes: Double
    ): Double {
        val timeFactor = deltaMinutes / 15.0

        val newEnergy = when {
            hr < hrLow -> {
                // Recovery
                currentEnergy + (hrLow - hr) * 0.1 * recoveryFactor * timeFactor
            }
            hr > hrHigh -> {
                // Drain
                currentEnergy - (hr - hrHigh) * 0.15 * drainFactor * hrvMultiplier * timeFactor
            }
            else -> {
                currentEnergy
            }
        }

        return newEnergy.coerceIn(0.0, 100.0)
    }

    /**
     * calculates HRV (RMSSD-Approximation) from HR-data.
     *
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
     * calculates Drain-Multiplier from actual HRV vs Baseline.
     *
     * - ratio < lowThreshold (0.7):  1.3x Drain
     * - ratio > highThreshold (1.3): 0.8x Drain
     * - between:                  Normal → 1.0x Drain
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