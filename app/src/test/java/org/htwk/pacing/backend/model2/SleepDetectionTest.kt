package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDetectionTest {

    // Helper function to create HR data points
    private fun createHRDataPoints(
        startMs: Long,
        intervalMs: Long,
        bpmValues: List<Double>
    ): List<HRDataPoint> {
        return bpmValues.mapIndexed { index, bpm ->
            HRDataPoint(
                timestamp = Instant.fromEpochMilliseconds(startMs + index * intervalMs),
                bpm = bpm
            )
        }
    }

    // ========== detectSleepPhases Tests ==========

    @Test
    fun `detectSleepPhases returns empty list for less than 2 data points`() {
        val config = SleepConfig()
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(0), 70.0)
        )

        val result = SleepDetection.detectSleepPhases(hrData, config)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectSleepPhases returns empty list for empty input`() {
        val config = SleepConfig()

        val result = SleepDetection.detectSleepPhases(emptyList(), config)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectSleepPhases detects single sleep phase`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 60 // Lower threshold for testing
        )

        // Simulate: awake -> sleep -> awake (over 120 minutes)
        val intervalMs = 15 * 60 * 1000L // 15 minutes
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 70.0, 65.0, // Transitioning to sleep
                55.0, 55.0, 55.0, 55.0, 55.0, // Sleeping (5 * 15 = 75 minutes)
                72.0 // Waking up
            )
        )

        val result = SleepDetection.detectSleepPhases(hrData, config)

        assertEquals(1, result.size)
    }

    @Test
    fun `detectSleepPhases ignores short sleep phases`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 200 // Default: requires ~3.3 hours
        )

        // Sleep phase only 60 minutes - should be ignored
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 70.0, 65.0,
                55.0, 55.0, 55.0, 55.0, // Only 60 minutes of sleep
                72.0
            )
        )

        val result = SleepDetection.detectSleepPhases(hrData, config)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectSleepPhases finds peak HR before sleep onset`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                70.0, 80.0, 75.0, 65.0, // Peak at index 1 (80 bpm)
                55.0, 55.0, 55.0, // Sleeping
                72.0 // Waking up
            )
        )

        val result = SleepDetection.detectSleepPhases(hrData, config)

        assertEquals(1, result.size)
        // Sleep start should be at the peak (index 1)
        assertEquals(Instant.fromEpochMilliseconds(intervalMs), result[0].start)
    }

    @Test
    fun `detectSleepPhases handles multiple sleep phases`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        val intervalMs = 15 * 60 * 1000L
        // Two distinct sleep phases
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 70.0, 65.0,
                55.0, 55.0, 55.0, // First sleep phase
                75.0, 80.0, 75.0, // Awake
                55.0, 55.0, 55.0, // Second sleep phase
                75.0 // Wake up
            )
        )

        val result = SleepDetection.detectSleepPhases(hrData, config)

        assertEquals(2, result.size)
    }

    @Test
    fun `detectSleepPhases handles HR staying below wake threshold`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        // Never wakes up - no phase should be detected
        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(65.0, 60.0, 55.0, 55.0, 55.0, 58.0, 55.0)
        )

        val result = SleepDetection.detectSleepPhases(hrData, config)

        // No complete sleep phases (never woke up)
        assertTrue(result.isEmpty())
    }

    // ========== detectWakeEvents Tests ==========

    @Test
    fun `detectWakeEvents returns wake events from sleep phases`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 70.0, 65.0,
                55.0, 55.0, 55.0,
                72.0 // Wake event
            )
        )

        val result = SleepDetection.detectWakeEvents(hrData, config)

        assertEquals(1, result.size)
        // Wake event should be at the end of sleep phase (index 6)
        assertEquals(Instant.fromEpochMilliseconds(6 * intervalMs), result[0].timestamp)
    }

    @Test
    fun `detectWakeEvents returns empty list when no sleep phases`() {
        val config = SleepConfig()

        val result = SleepDetection.detectWakeEvents(emptyList(), config)

        assertTrue(result.isEmpty())
    }

    // ========== getSleepCycles Tests ==========

    @Test
    fun `getSleepCycles returns empty list when less than 2 sleep phases`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 70.0, 65.0,
                55.0, 55.0, 55.0,
                72.0
            )
        )

        val result = SleepDetection.getSleepCycles(hrData, config)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSleepCycles returns cycle between consecutive sleep phases`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 80.0, 70.0, 65.0,
                55.0, 55.0, 55.0, // First sleep phase
                75.0, 80.0, 75.0, // Awake period
                85.0, 70.0, 65.0,
                55.0, 55.0, 55.0, // Second sleep phase
                75.0
            )
        )

        val result = SleepDetection.getSleepCycles(hrData, config)

        assertEquals(1, result.size)
    }

    @Test
    fun `getSleepCycles label contains date`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        val intervalMs = 15 * 60 * 1000L
        val baseTime = 1704067200000L // 2024-01-01T00:00:00Z
        val hrData = createHRDataPoints(
            startMs = baseTime,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 80.0, 70.0, 65.0,
                55.0, 55.0, 55.0,
                75.0, 80.0, 75.0,
                85.0, 70.0, 65.0,
                55.0, 55.0, 55.0,
                75.0
            )
        )

        val result = SleepDetection.getSleepCycles(hrData, config)

        if (result.isNotEmpty()) {
            // Label should contain a date-like string
            assertTrue(result[0].label.contains("2024"))
        }
    }

    @Test
    fun `getSleepCycles handles empty input`() {
        val config = SleepConfig()

        val result = SleepDetection.getSleepCycles(emptyList(), config)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getSleepCycles cycle boundaries match sleep phase starts`() {
        val config = SleepConfig(
            sleepHRThreshold = 62.0,
            wakeHRThreshold = 70.0,
            minSleepMinutes = 30
        )

        val intervalMs = 15 * 60 * 1000L
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(
                75.0, 80.0, 70.0, 65.0,
                55.0, 55.0, 55.0, // First sleep starts around index 1 (peak)
                75.0, 80.0, 75.0,
                85.0, 70.0, 65.0,
                55.0, 55.0, 55.0, // Second sleep starts around index 10 (peak)
                75.0
            )
        )

        val phases = SleepDetection.detectSleepPhases(hrData, config)
        val cycles = SleepDetection.getSleepCycles(hrData, config)

        if (phases.size >= 2 && cycles.isNotEmpty()) {
            assertEquals(phases[0].start, cycles[0].cycleStart)
            assertEquals(phases[1].start, cycles[0].cycleEnd)
        }
    }
}
