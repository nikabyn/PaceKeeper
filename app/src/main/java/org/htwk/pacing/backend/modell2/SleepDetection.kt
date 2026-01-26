package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant
import kotlin.math.max
import kotlin.math.min

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

        val phases = mutableListOf<SleepPhase>()
        var inSleep = false
        var sleepStart: Instant? = null

        for (i in hrAgg.indices) {
            val hr = hrAgg[i].bpm
            val ts = hrAgg[i].timestamp

            if (!inSleep && hr < sleepCfg.sleepHRThreshold) {
                // Look back: when did HR start to drop?
                var peakIdx = i
                for (j in (max(0, i - 20) until i).reversed()) {
                    if (hrAgg[j].bpm > hrAgg[peakIdx].bpm) {
                        peakIdx = j
                    }
                }
                inSleep = true
                sleepStart = hrAgg[peakIdx].timestamp
            }

            if (inSleep && hr >= sleepCfg.wakeHRThreshold) {
                val sleepDuration = sleepStart?.let { start ->
                    (ts.toEpochMilliseconds() - start.toEpochMilliseconds()) / 60000.0
                } ?: 0.0

                if (sleepDuration >= sleepCfg.minSleepMinutes) {
                    sleepStart?.let { start ->
                        phases.add(SleepPhase(start = start, end = ts))
                    }
                }
                inSleep = false
                sleepStart = null
            }
        }

        return phases
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
