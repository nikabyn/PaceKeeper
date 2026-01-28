package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant
import kotlin.math.max

/**
 * Sleep detection logic translated from TypeScript optimizer.ts
 */
object SleepDetection {

    /**
     * Detects sleep phases based on heart rate thresholds.
     * Looks back to find when HR started to drop (peak) before falling below sleep threshold.
     */
    fun detectSleepPhases(
        hrAgg: List<HRDataPoint>,
        sleepCfg: SleepConfig
    ): List<SleepPhase> {
        if (hrAgg.size < 2) return emptyList()

        data class State(
            val inSleep: Boolean = false,
            val sleepStart: Instant? = null,
            val phases: List<SleepPhase> = emptyList()
        )

        return hrAgg.foldIndexed(State()) { i, state, point ->
            when {
                !state.inSleep && point.bpm < sleepCfg.sleepHRThreshold -> {
                    state.copy(inSleep = true, sleepStart = findPeakTimestamp(hrAgg, i))
                }
                state.inSleep && point.bpm >= sleepCfg.wakeHRThreshold -> {
                    val newPhases = state.sleepStart?.let { start ->
                        val duration = (point.timestamp.toEpochMilliseconds() - start.toEpochMilliseconds()) / 60000.0
                        if (duration >= sleepCfg.minSleepMinutes) state.phases + SleepPhase(start, point.timestamp)
                        else state.phases
                    } ?: state.phases
                    State(inSleep = false, sleepStart = null, phases = newPhases)
                }
                else -> state
            }
        }.phases
    }

    private fun findPeakTimestamp(hrAgg: List<HRDataPoint>, currentIdx: Int): Instant {
        var peakIdx = currentIdx
        for (j in (max(0, currentIdx - 20) until currentIdx).reversed()) {
            if (hrAgg[j].bpm > hrAgg[peakIdx].bpm) peakIdx = j
        }
        return hrAgg[peakIdx].timestamp
    }

    /**
     * Detects wake events (end of each sleep phase).
     */
    fun detectWakeEvents(
        hrAgg: List<HRDataPoint>,
        sleepCfg: SleepConfig
    ): List<WakeEvent> {
        val phases = detectSleepPhases(hrAgg, sleepCfg)
        return phases.map { WakeEvent(timestamp = it.end) }
    }

    /**
     * Gets sleep cycles (periods between consecutive sleep phases).
     */
    fun getSleepCycles(
        hrAgg: List<HRDataPoint>,
        sleepCfg: SleepConfig
    ): List<SleepCycle> {
        val phases = detectSleepPhases(hrAgg, sleepCfg)
        if (phases.size < 2) return emptyList()

        return phases.zipWithNext().map { (current, next) ->
            SleepCycle(
                cycleStart = current.start,
                cycleEnd = next.start,
                label = current.start.toString().substringBefore("T")
            )
        }
    }
}
