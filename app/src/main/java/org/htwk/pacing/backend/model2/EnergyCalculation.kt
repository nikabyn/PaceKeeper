package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant

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
object EnergyCalculation {
    val DEFAULT_HRV_DRAIN_CONFIG = HRVDrainConfig()

    // ========================================================================
    // HAUPT-ENTRY-POINT
    // ========================================================================

    /**
     * calculates energy with HRV-Drain and Anchoring from validated user inputs
     *
     * ZENTRAL METHOD for energy prediction
     *
     * @param hrAgg Aggregierte HR-Daten (Optimizer.aggregateHR)
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
        val baseline = HRVDrain.calculateHRVBaseline(hrvData)

        for (i in hrAgg.indices) {
            val hr = hrAgg[i].bpm
            val ts = hrAgg[i].timestamp
            val tsMs = ts.toEpochMilliseconds()

            val deltaMinutes = if (i > 0) {
                (tsMs - hrAgg[i - 1].timestamp.toEpochMilliseconds()) / 60000.0
            } else {
                aggregationMinutes.toDouble()
            }

            val currentHRV = HRVDrain.getHRVAtTime(hrvData, ts)
            val hrvMultiplier = HRVDrain.getDrainMultiplier(currentHRV, baseline, hrvConfig)

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