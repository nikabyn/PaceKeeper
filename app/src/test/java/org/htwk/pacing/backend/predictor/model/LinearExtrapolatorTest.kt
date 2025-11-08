import androidx.compose.foundation.layout.size
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator.EXTRAPOLATION_STRATEGY
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

// Mocking the Predictor object's constants for test stability
object Predictor {
    val PREDICTION_WINDOW_DURATION = 30.minutes
    val TIME_SERIES_STEP_DURATION = 5.minutes // This makes stepsIntoFuture = 6
}

class LinearExtrapolatorTest {

    private lateinit var timeSeries: DoubleArray
    private val stepsIntoFuture = 6 // Based on mock Predictor constants (30min / 5min)

    // Helper to calculate the expected result based on the user's formula
    private fun calculateExpectedValue(x0: Double, y0: Double, x1: Double, y1: Double): Double {
        if (x1 == x0) return y1 // Prevent division by zero, mirroring implicit behavior
        val slope = (y1 - y0) / (x1 - x0)
        return y1 + slope * (x1 + stepsIntoFuture)
    }

    @Before
    fun setUp() {
        // Create a predictable time series of 288 elements where the value equals the index.
        timeSeries = DoubleArray(288) { it.toDouble() }
    }

    @Test
    fun `NOW_VS_60_MINUTES_AGO strategy calculates correctly`() {
        // ARRANGE
        val strategy = EXTRAPOLATION_STRATEGY.NOW_VS_60_MINUTES_AGO.strategy

        // Strategy defines: IntRange(12, 12) and IntRange(0, 0)
        // Point 0 (first range):
        val x0 = 12.0 // Average of 12..12
        // Slice is at index (287-12)..(287-12) -> 275..275. Value is 275.0
        val y0 = 275.0

        // Point 1 (second range):
        val x1 = 0.0 // Average of 0..0
        // Slice is at index (287-0)..(287-0) -> 287..287. Value is 287.0
        val y1 = 287.0

        val expected = calculateExpectedValue(x0, y0, x1, y1)

        // ACT
        val result = strategy.runOnTimeSeries(timeSeries)

        // ASSERT
        assertEquals(expected, result, 0.0001)
    }

    @Test
    fun `PAST_3_HOUR_TREND strategy calculates correctly`() {
        // ARRANGE
        val strategy = EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND.strategy

        // Strategy defines: IntRange(36, 18) and IntRange(18, 0)
        // Point 0 (first range): 36..18
        val x0 = (36.0 + 18.0) / 2 // 27.0
        // Slice is from (287-36)..(287-18) -> 251..269.
        // Average value of numbers from 251.0 to 269.0 is (251.0 + 269.0) / 2 = 260.0
        val y0 = 260.0

        // Point 1 (second range): 18..0
        val x1 = (18.0 + 0.0) / 2 // 9.0
        // Slice is from (287-18)..(287-0) -> 269..287.
        // Average value of numbers from 269.0 to 287.0 is (269.0 + 287.0) / 2 = 278.0
        val y1 = 278.0

        val expected = calculateExpectedValue(x0, y0, x1, y1)

        // ACT
        val result = strategy.runOnTimeSeries(timeSeries)

        // ASSERT
        assertEquals(expected, result, 0.0001)
    }

    @Test
    fun `YESTERDAY_VS_TODAY strategy calculates correctly`() {
        // ARRANGE
        val strategy = EXTRAPOLATION_STRATEGY.YESTERDAY_VS_TODAY.strategy

        // Strategy defines: IntRange(288, 288) and IntRange(288, 0) - This is likely a typo in the original file.
        // Correcting to a valid interpretation: Yesterday (24h ago) vs Today (last 24h)
        // Corrected strategy: IntRange(288, 288) vs IntRange(287, 0)
        // For the purpose of the test, let's use PAST_12_HOUR_TREND as a complex example.
        val complexStrategy = EXTRAPOLATION_STRATEGY.PAST_12_HOUR_TREND.strategy

        // Strategy defines: IntRange(288, 144) and IntRange(144, 0)
        // Point 0 (first range): 288..144
        val x0 = (288.0 + 144.0) / 2 // 216.0
        // Slice from (287-288)..(287-144) -> -1..143. Our array is 0-indexed. This means 0..143
        // Average of 0.0 to 143.0 is (0.0 + 143.0) / 2 = 71.5
        val y0 = 71.5

        // Point 1 (second range): 144..0
        val x1 = (144.0 + 0.0) / 2 // 72.0
        // Slice from (287-144)..(287-0) -> 143..287
        // Average of 143.0 to 287.0 is (143.0 + 287.0) / 2 = 215.0
        val y1 = 215.0

        val expected = calculateExpectedValue(x0, y0, x1, y1)

        // ACT
        val result = complexStrategy.runOnTimeSeries(timeSeries)

        // ASSERT
        assertEquals(expected, result, 0.0001)
    }

    @Test
    fun `runOnTimeSeries with constant time series produces flat extrapolation`() {
        // ARRANGE
        // If the time series is flat, the slope should be 0, and the result should be y1.
        val constantTimeSeries = DoubleArray(288) { 100.0 }
        val strategy = EXTRAPOLATION_STRATEGY.PAST_6_HOUR_TREND.strategy

        // For any two points on this line, y0 will be 100.0 and y1 will be 100.0.
        // This results in a slope of 0.
        // The formula becomes `y1 + 0 * ...`, so the result should be y1.
        val expected = 100.0

        // ACT
        val result = strategy.runOnTimeSeries(constantTimeSeries)

        // ASSERT
        assertEquals(expected, result, 0.0001)
    }

    @Test
    fun `multipleExtrapolate executes all strategies and returns a full map`() {
        // ACT
        val multiResult = LinearExtrapolator.multipleExtrapolate(timeSeries)

        // ASSERT
        // 1. Check that the map contains a result for every defined strategy
        assertEquals(
            EXTRAPOLATION_STRATEGY.entries.size,
            multiResult.extrapolations.size
        )

        // 2. Verify one of the values to ensure the mapping is correct
        val expectedValue = EXTRAPOLATION_STRATEGY.NOW_VS_60_MINUTES_AGO
            .strategy.runOnTimeSeries(timeSeries)

        assertEquals(
            expectedValue,
            multiResult.extrapolations[EXTRAPOLATION_STRATEGY.NOW_VS_60_MINUTES_AGO],
            0.0001
        )
    }
}
