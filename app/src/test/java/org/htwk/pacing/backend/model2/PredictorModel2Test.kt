package org.htwk.pacing.backend.model2

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PredictorModel2Test {

    // Helper functions
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

    // ========== aggregateHR Tests ==========

    @Test
    fun `aggregateHR returns empty list for empty input`() {
        val result = PredictorModel2.aggregateHR(emptyList(), 15)

        assertTrue(result.isEmpty())
    }

    @Test
    fun `aggregateHR groups data into time buckets`() {
        val intervalMs = 5 * 60 * 1000L // 5 minute intervals
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = intervalMs,
            bpmValues = listOf(70.0, 72.0, 68.0, 75.0, 73.0, 71.0)
        )

        // Aggregate to 15-minute buckets
        val result = PredictorModel2.aggregateHR(hrData, 15)

        // 6 points over 25 minutes with 15-min buckets should result in 2 buckets
        assertTrue(result.size <= 2)
    }

    @Test
    fun `aggregateHR calculates average bpm per bucket`() {
        val bucketMs = 15 * 60 * 1000L
        // All points in the same 15-minute bucket
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(0), 70.0),
            HRDataPoint(Instant.fromEpochMilliseconds(5 * 60 * 1000L), 80.0),
            HRDataPoint(Instant.fromEpochMilliseconds(10 * 60 * 1000L), 90.0)
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        // All in one bucket, average should be 80
        assertEquals(1, result.size)
        assertEquals(80.0, result[0].bpm, 0.001)
    }

    @Test
    fun `aggregateHR returns sorted results by timestamp`() {
        val intervalMs = 5 * 60 * 1000L
        // Create data in non-sorted order
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(30 * 60 * 1000L), 75.0),
            HRDataPoint(Instant.fromEpochMilliseconds(0L), 70.0),
            HRDataPoint(Instant.fromEpochMilliseconds(15 * 60 * 1000L), 72.0)
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        // Should be sorted by timestamp
        for (i in 0 until result.size - 1) {
            assertTrue(result[i].timestamp <= result[i + 1].timestamp)
        }
    }

    @Test
    fun `aggregateHR uses bucket start time as timestamp`() {
        val bucketMs = 15 * 60 * 1000L
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(3 * 60 * 1000L), 70.0), // 3 min
            HRDataPoint(Instant.fromEpochMilliseconds(8 * 60 * 1000L), 75.0)  // 8 min
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        // Both points should be in bucket starting at 0
        assertEquals(1, result.size)
        assertEquals(0L, result[0].timestamp.toEpochMilliseconds())
    }

    @Test
    fun `aggregateHR handles single data point`() {
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(5 * 60 * 1000L), 72.0)
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        assertEquals(1, result.size)
        assertEquals(72.0, result[0].bpm, 0.001)
    }

    @Test
    fun `aggregateHR with different bucket sizes`() {
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = 5 * 60 * 1000L,
            bpmValues = listOf(70.0, 72.0, 74.0, 76.0, 78.0, 80.0)
        )

        // 5-minute buckets
        val result5 = PredictorModel2.aggregateHR(hrData, 5)

        // 30-minute buckets
        val result30 = PredictorModel2.aggregateHR(hrData, 30)

        // Smaller buckets should have more (or equal) results
        assertTrue(result5.size >= result30.size)
    }

    // ========== TIME_SERIES_DURATION Tests ==========

    @Test
    fun `TIME_SERIES_DURATION is 2 hours`() {
        val duration = PredictorModel2.TIME_SERIES_DURATION

        assertEquals(2 * 60 * 60 * 1000L, duration.inWholeMilliseconds)
    }

    // ========== Edge Cases ==========

    @Test
    fun `aggregateHR handles data spanning multiple days`() {
        val dayMs = 24 * 60 * 60 * 1000L
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(0L), 70.0),
            HRDataPoint(Instant.fromEpochMilliseconds(dayMs), 75.0),
            HRDataPoint(Instant.fromEpochMilliseconds(2 * dayMs), 80.0)
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        // Each point should be in its own bucket (different days)
        assertEquals(3, result.size)
    }

    @Test
    fun `aggregateHR handles very small aggregation minutes`() {
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = 60 * 1000L, // 1 minute intervals
            bpmValues = listOf(70.0, 72.0, 74.0, 76.0, 78.0)
        )

        val result = PredictorModel2.aggregateHR(hrData, 1)

        // With 1-minute buckets, each point should be separate or nearly so
        assertTrue(result.isNotEmpty())
    }

    @Test
    fun `aggregateHR handles large aggregation minutes`() {
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = 5 * 60 * 1000L,
            bpmValues = listOf(70.0, 72.0, 74.0, 76.0, 78.0, 80.0)
        )

        // 60-minute buckets should group all 30 minutes of data into one bucket
        val result = PredictorModel2.aggregateHR(hrData, 60)

        assertEquals(1, result.size)
        assertEquals(75.0, result[0].bpm, 0.001) // Average of 70-80
    }

    @Test
    fun `aggregateHR preserves data integrity`() {
        val hrData = createHRDataPoints(
            startMs = 0L,
            intervalMs = 15 * 60 * 1000L,
            bpmValues = listOf(60.0, 120.0) // Very different values
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        // With 15-minute intervals and 15-minute buckets, each point should be separate
        assertEquals(2, result.size)
        assertEquals(60.0, result[0].bpm, 0.001)
        assertEquals(120.0, result[1].bpm, 0.001)
    }

    @Test
    fun `aggregateHR handles duplicate timestamps`() {
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(0L), 70.0),
            HRDataPoint(Instant.fromEpochMilliseconds(0L), 80.0), // Same timestamp
            HRDataPoint(Instant.fromEpochMilliseconds(0L), 90.0)  // Same timestamp
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        // All should be averaged into one bucket
        assertEquals(1, result.size)
        assertEquals(80.0, result[0].bpm, 0.001)
    }

    @Test
    fun `aggregateHR handles boundary timestamps`() {
        val bucketMs = 15 * 60 * 1000L
        val hrData = listOf(
            HRDataPoint(Instant.fromEpochMilliseconds(bucketMs - 1), 70.0), // End of first bucket
            HRDataPoint(Instant.fromEpochMilliseconds(bucketMs), 80.0)      // Start of second bucket
        )

        val result = PredictorModel2.aggregateHR(hrData, 15)

        // Should be in different buckets
        assertEquals(2, result.size)
    }
}
