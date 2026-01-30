package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HRVDrainTest {

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

    // ========== calculateHRVFromHR Tests ==========

    @Test
    fun `calculateHRVFromHR returns empty list for less than 5 data points`() {
        val hrData = createHRDataPoints(0L, 60000L, listOf(70.0, 72.0, 68.0, 71.0))

        val result = HRVDrain.calculateHRVFromHR(hrData)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `calculateHRVFromHR returns HRV points for sufficient data`() {
        // Create 10 HR data points over 10 minutes
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = 60000L, // 1 minute intervals
            bpmValues = listOf(70.0, 72.0, 68.0, 71.0, 69.0, 73.0, 70.0, 68.0, 72.0, 71.0)
        )

        val result = HRVDrain.calculateHRVFromHR(hrData, windowMinutes = 5)

        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `calculateHRVFromHR filters out implausible RMSSD values above 50`() {
        // Create highly variable HR data that would produce high RMSSD
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = 60000L,
            bpmValues = listOf(40.0, 120.0, 40.0, 120.0, 40.0, 120.0, 40.0, 120.0, 40.0, 120.0)
        )

        val result = HRVDrain.calculateHRVFromHR(hrData, windowMinutes = 5)

        // All results should have RMSSD < 50
        result.forEach { point ->
            assertTrue("RMSSD should be less than 50, was ${point.rmssd}", point.rmssd < 50.0)
        }
    }

    @Test
    fun `calculateHRVFromHR preserves timestamp from original HR data`() {
        val startMs = 1000000L
        val hrData = createHRDataPoints(
            startMs = startMs,
            intervalMs = 60000L,
            bpmValues = listOf(70.0, 71.0, 69.0, 72.0, 68.0, 71.0, 70.0, 69.0, 71.0, 70.0)
        )

        val result = HRVDrain.calculateHRVFromHR(hrData, windowMinutes = 5)

        // Result timestamps should match some of the input timestamps
        result.forEach { hrvPoint ->
            assertTrue(
                "HRV timestamp should be from input data",
                hrData.any { it.timestamp == hrvPoint.timestamp }
            )
        }
    }

    // ========== calculateHRVBaseline Tests ==========

    @Test
    fun `calculateHRVBaseline returns default 50 for empty list`() {
        val result = HRVDrain.calculateHRVBaseline(emptyList())

        assertEquals(50.0, result, 0.001)
    }

    @Test
    fun `calculateHRVBaseline returns median for odd number of values`() {
        val hrvData = listOf(
            HRVPoint(Instant.fromEpochMilliseconds(0), 10.0),
            HRVPoint(Instant.fromEpochMilliseconds(1000), 20.0),
            HRVPoint(Instant.fromEpochMilliseconds(2000), 30.0)
        )

        val result = HRVDrain.calculateHRVBaseline(hrvData)

        assertEquals(20.0, result, 0.001)
    }

    @Test
    fun `calculateHRVBaseline returns median for even number of values`() {
        val hrvData = listOf(
            HRVPoint(Instant.fromEpochMilliseconds(0), 10.0),
            HRVPoint(Instant.fromEpochMilliseconds(1000), 20.0),
            HRVPoint(Instant.fromEpochMilliseconds(2000), 30.0),
            HRVPoint(Instant.fromEpochMilliseconds(3000), 40.0)
        )

        val result = HRVDrain.calculateHRVBaseline(hrvData)

        assertEquals(25.0, result, 0.001) // (20 + 30) / 2
    }

    @Test
    fun `calculateHRVBaseline sorts values correctly`() {
        // Values not in sorted order
        val hrvData = listOf(
            HRVPoint(Instant.fromEpochMilliseconds(0), 30.0),
            HRVPoint(Instant.fromEpochMilliseconds(1000), 10.0),
            HRVPoint(Instant.fromEpochMilliseconds(2000), 20.0)
        )

        val result = HRVDrain.calculateHRVBaseline(hrvData)

        assertEquals(20.0, result, 0.001)
    }

    // ========== getHRVAtTime Tests ==========

    @Test
    fun `getHRVAtTime returns null for empty list`() {
        val result = HRVDrain.getHRVAtTime(emptyList(), Instant.fromEpochMilliseconds(1000))

        assertNull(result)
    }

    @Test
    fun `getHRVAtTime returns closest HRV within maxDiff`() {
        val hrvData = listOf(
            HRVPoint(Instant.fromEpochMilliseconds(0), 10.0),
            HRVPoint(Instant.fromEpochMilliseconds(60000), 20.0),
            HRVPoint(Instant.fromEpochMilliseconds(120000), 30.0)
        )

        val result = HRVDrain.getHRVAtTime(
            hrvData,
            Instant.fromEpochMilliseconds(65000),
            maxDiffMs = 5 * 60 * 1000L
        )

        assertEquals(20.0, result!!, 0.001)
    }

    @Test
    fun `getHRVAtTime returns null when no HRV within maxDiff`() {
        val hrvData = listOf(
            HRVPoint(Instant.fromEpochMilliseconds(0), 10.0)
        )

        val result = HRVDrain.getHRVAtTime(
            hrvData,
            Instant.fromEpochMilliseconds(600000), // 10 minutes later
            maxDiffMs = 60000L // only 1 minute tolerance
        )

        assertNull(result)
    }

    @Test
    fun `getHRVAtTime returns exact match`() {
        val hrvData = listOf(
            HRVPoint(Instant.fromEpochMilliseconds(100000), 25.5)
        )

        val result = HRVDrain.getHRVAtTime(
            hrvData,
            Instant.fromEpochMilliseconds(100000)
        )

        assertEquals(25.5, result!!, 0.001)
    }

    // ========== getDrainMultiplier Tests ==========

    @Test
    fun `getDrainMultiplier returns normalMultiplier when HRV is null`() {
        val config = HRVDrainConfig()

        val result = HRVDrain.getDrainMultiplier(null, 30.0, config)

        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `getDrainMultiplier returns lowHRVMultiplier when ratio below lowThreshold`() {
        val config = HRVDrainConfig(
            lowThreshold = 0.7,
            lowHRVMultiplier = 1.5
        )
        val baseline = 30.0
        val currentHRV = 15.0 // ratio = 0.5, below 0.7

        val result = HRVDrain.getDrainMultiplier(currentHRV, baseline, config)

        assertEquals(1.5, result, 0.001)
    }

    @Test
    fun `getDrainMultiplier returns highHRVMultiplier when ratio above highThreshold`() {
        val config = HRVDrainConfig(
            highThreshold = 1.3,
            highHRVMultiplier = 0.5
        )
        val baseline = 30.0
        val currentHRV = 45.0 // ratio = 1.5, above 1.3

        val result = HRVDrain.getDrainMultiplier(currentHRV, baseline, config)

        assertEquals(0.5, result, 0.001)
    }

    @Test
    fun `getDrainMultiplier returns normalMultiplier when ratio is between thresholds`() {
        val config = HRVDrainConfig(
            lowThreshold = 0.7,
            highThreshold = 1.3,
            normalHRVMultiplier = 1.0
        )
        val baseline = 30.0
        val currentHRV = 30.0 // ratio = 1.0, between thresholds

        val result = HRVDrain.getDrainMultiplier(currentHRV, baseline, config)

        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `getDrainMultiplier handles edge case at lowThreshold`() {
        val config = HRVDrainConfig(
            lowThreshold = 0.7,
            lowHRVMultiplier = 1.5,
            normalHRVMultiplier = 1.0
        )
        val baseline = 100.0
        val currentHRV = 70.0 // ratio = 0.7, exactly at threshold

        val result = HRVDrain.getDrainMultiplier(currentHRV, baseline, config)

        // At exactly the threshold, should be normal (not low)
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `getDrainMultiplier handles edge case at highThreshold`() {
        val config = HRVDrainConfig(
            highThreshold = 1.3,
            highHRVMultiplier = 0.5,
            normalHRVMultiplier = 1.0
        )
        val baseline = 100.0
        val currentHRV = 130.0 // ratio = 1.3, exactly at threshold

        val result = HRVDrain.getDrainMultiplier(currentHRV, baseline, config)

        // At exactly the threshold, should be normal (not high)
        assertEquals(1.0, result, 0.001)
    }

    @Test
    fun `getDrainMultiplier uses custom config values`() {
        val config = HRVDrainConfig(
            lowThreshold = 0.5,
            highThreshold = 2.0,
            lowHRVMultiplier = 2.0,
            normalHRVMultiplier = 1.2,
            highHRVMultiplier = 0.3
        )
        val baseline = 20.0
        val currentHRV = 8.0 // ratio = 0.4, below 0.5

        val result = HRVDrain.getDrainMultiplier(currentHRV, baseline, config)

        assertEquals(2.0, result, 0.001)
    }
}
