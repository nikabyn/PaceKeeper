import org.htwk.pacing.backend.helpers.plotTimeSeriesExtrapolationsWithPython
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator.EXTRAPOLATION_STRATEGY
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

class LinearExtrapolatorTest {
    private var timeSeries: DoubleArray = DoubleArray(288) { i ->
        val x = i / 288.0 //time normalization to array range
        (i * 0.8) + (kotlin.math.sin(x * 5.5 * kotlin.math.PI) * 20) +
                (kotlin.math.sin(x * 3.3 * kotlin.math.PI) * 36) +
                (kotlin.math.cos(x * 9.7 * kotlin.math.PI) * 9) +
                (kotlin.math.cos(x * 13.4 * kotlin.math.PI) * 7.6) +
                (kotlin.math.sin(x * kotlin.math.sin(x * 1.3) * 29.4 * kotlin.math.PI) * 3.6) +
                50
    }

    @Ignore("This test is for manual visualization inspection and requires a graphical environment.")
    @Test
    fun `visualize the non-linear timeSeries using an external script`() {
        println("Preparing to plot time series data...")

        val result = LinearExtrapolator.multipleExtrapolate(timeSeries)

        result.extrapolations.entries.forEach { (strategy, extrapolation) ->
            println("Strategy: $strategy")
            val extr = extrapolation
            println("First Point: ${extr.firstPoint}")
            println("Second Point: ${extr.secondPoint}")
            println("Result Point: ${extr.resultPoint}")
        }

        plotTimeSeriesExtrapolationsWithPython(timeSeries, result.extrapolations)

        //this test's primary purpose is visualization, not assertion.
        //add a trivial assertion to not confuse test runner
        assertEquals(288, timeSeries.size)
        println("Plotting finished.")
    }

    @Test
    fun `multipleExtrapolate returns the correct, pinned results for a known non-linear timeSeries`() {
        // This "golden result set" is calculated and pinned for the specific non-linear timeSeries in setUp().
        // If any underlying logic changes, these tests will fail, providing a strong regression guard.
        val expectedResults = mapOf(
            EXTRAPOLATION_STRATEGY.NOW_VS_30_MINUTES_AGO to 269.6858,
            EXTRAPOLATION_STRATEGY.NOW_VS_60_MINUTES_AGO to 259.7337,
            EXTRAPOLATION_STRATEGY.NOW_VS_90_MINUTES_AGO to 246.8574,
            EXTRAPOLATION_STRATEGY.NOW_VS_120_MINUTES_AGO to 238.2801,

            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_1_HOUR_AGO to 229.6442,
            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_2_HOURS_AGO to 218.6667,
            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_3_HOURS_AGO to 212.0154,

            EXTRAPOLATION_STRATEGY.NOW_VS_1_HOUR_TREND to 265.1736,
            EXTRAPOLATION_STRATEGY.NOW_VS_3_HOUR_TREND to 241.9941,
            EXTRAPOLATION_STRATEGY.NOW_VS_6_HOUR_TREND to 227.2441,
            EXTRAPOLATION_STRATEGY.NOW_VS_12_HOUR_TREND to 224.4468,

            EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND to 190.9567,
            EXTRAPOLATION_STRATEGY.PAST_6_HOUR_TREND to 212.9162,
            EXTRAPOLATION_STRATEGY.PAST_12_HOUR_TREND to 315.6367,

            EXTRAPOLATION_STRATEGY.LAST_HOUR_AVERAGE_VS_1_DAY_AGO to 235.6446,
            EXTRAPOLATION_STRATEGY.YESTERDAY_VS_TODAY to 290.3910

        )

        val result = LinearExtrapolator.multipleExtrapolate(timeSeries)

        assertEquals(
            "The number of strategies tested should match the number of results.",
            expectedResults.size,
            result.extrapolations.size
        )

        // Check each individual result against the golden set
        for ((strategy, expectedValue) in expectedResults) {
            val actualValue = result.extrapolations[strategy]?.resultPoint?.second
            assertEquals(
                "Mismatch for strategy: $strategy",
                expectedValue,
                actualValue!!,
                0.0001
            )
        }
    }

    @Test
    fun `runOnTimeSeries with constant time series produces flat extrapolation`() {
        // test as sanity check for slope calculation.
        val constantTimeSeries = DoubleArray(288) { 100.0 }
        val strategy = EXTRAPOLATION_STRATEGY.PAST_6_HOUR_TREND.strategy
        val expected = 100.0 //flat line, slope is 0, so result should be y1 (which is 100)

        val result = strategy.runOnTimeSeries(constantTimeSeries)

        assertEquals(expected, result.resultPoint.second, 0.0001)
    }


    @Test
    fun `PAST_3_HOUR_TREND strategy calculates correctly for non-linear data`() {
        val strategy = EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND.strategy
        val expected = 190.95672 //expected value for this specific case

        val result = strategy.runOnTimeSeries(timeSeries)

        assertEquals(expected, result.resultPoint.second, 0.0001)
    }
}
