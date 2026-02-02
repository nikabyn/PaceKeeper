import junit.framework.TestCase.assertEquals
import org.htwk.pacing.backend.helpers.plotTimeSeriesExtrapolationsWithPython
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.model.ExtrapolationPredictionModel.howFarInSamples
import org.htwk.pacing.backend.predictor.model.IPredictionModel
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator.EXTRAPOLATION_STRATEGY
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.junit.Ignore
import org.junit.Test

class LinearExtrapolatorTest {
    //some random time series with a general upward trend, used in the tests
    private var timeSeries: D1Array<Double> =
        mk.ndarray(DoubleArray(Predictor.TIME_SERIES_SAMPLE_COUNT) { i ->
            val x =
                i / Predictor.TIME_SERIES_SAMPLE_COUNT.toDouble() //time normalization to array range
            (i * 0.8) + (kotlin.math.sin(x * 5.5 * kotlin.math.PI) * 20) +
                    (kotlin.math.sin(x * 3.3 * kotlin.math.PI) * 36) +
                    (kotlin.math.cos(x * 9.7 * kotlin.math.PI) * 9) +
                    (kotlin.math.cos(x * 13.4 * kotlin.math.PI) * 7.6) +
                    (kotlin.math.sin(x * kotlin.math.sin(x * 1.3) * 29.4 * kotlin.math.PI) * 3.6) +
                    50
        })

    @Ignore("This test is for manual visualization inspection and requires a graphical environment.")
    @Test
    fun `visualize the non-linear timeSeries using an external script`() {
        println("Preparing to plot time series data...")

        val result = LinearExtrapolator.multipleExtrapolate(
            timeSeries,
            IPredictionModel.PredictionHorizon.FUTURE.howFarInSamples
        )

        result.extrapolations.entries.forEach { (strategy, extrapolation) ->
            println("Strategy: $strategy")
            val extr = extrapolation
            println("First Point: ${extr.firstPoint}")
            println("Second Point: ${extr.secondPoint}")
            println("Result Point: ${extr.resultPoint}")
        }

        plotTimeSeriesExtrapolationsWithPython(timeSeries.toDoubleArray(), result.extrapolations)

        //this test's primary purpose is visualization, not assertion.
        //add a trivial assertion to not confuse test runner
        assertEquals(Predictor.TIME_SERIES_SAMPLE_COUNT, timeSeries.size)
        println("Plotting finished.")
    }

    @Test
    fun `multipleExtrapolate returns the correct, pinned results for a known non-linear timeSeries`() {
        // This "golden result set" is calculated and pinned for the specific non-linear timeSeries in setUp().
        // If any underlying logic changes, these tests will fail, providing a strong regression guard.
        val expectedResults = mapOf(
            EXTRAPOLATION_STRATEGY.NOW_VS_30_MINUTES_AGO to 206.7107508280343,
            EXTRAPOLATION_STRATEGY.NOW_VS_60_MINUTES_AGO to 202.29527917401572,
            EXTRAPOLATION_STRATEGY.NOW_VS_90_MINUTES_AGO to 194.5303437533522,
            EXTRAPOLATION_STRATEGY.NOW_VS_120_MINUTES_AGO to 184.70585769890175,

            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_1_HOUR_AGO to 184.46591149454235,
            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_2_HOURS_AGO to 162.86423370246158,
            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_3_HOURS_AGO to 146.94319876439656,

            EXTRAPOLATION_STRATEGY.NOW_VS_1_HOUR_TREND to 203.7671030586887,
            EXTRAPOLATION_STRATEGY.NOW_VS_3_HOUR_TREND to 180.87242613486148,
            EXTRAPOLATION_STRATEGY.NOW_VS_6_HOUR_TREND to 159.54646160895584,
            EXTRAPOLATION_STRATEGY.NOW_VS_12_HOUR_TREND to 138.59527121701933,

            EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND to 128.5986845799698,
            EXTRAPOLATION_STRATEGY.PAST_6_HOUR_TREND to 97.7967596294936,
            EXTRAPOLATION_STRATEGY.PAST_12_HOUR_TREND to 127.20341595525497,

            EXTRAPOLATION_STRATEGY.LAST_HOUR_AVERAGE_VS_1_DAY_AGO to 139.47634501181722,
            EXTRAPOLATION_STRATEGY.YESTERDAY_VS_TODAY to 240.7210197834492
        )

        val result = LinearExtrapolator.multipleExtrapolate(
            timeSeries,
            IPredictionModel.PredictionHorizon.FUTURE.howFarInSamples
        )

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
        val constantTimeSeries =
            mk.ndarray(DoubleArray(Predictor.TIME_SERIES_SAMPLE_COUNT) { 100.0 })
        val strategy = EXTRAPOLATION_STRATEGY.PAST_6_HOUR_TREND.strategy
        val expected = 100.0 //flat line, slope is 0, so result should be y1 (which is 100)

        val result = strategy.runOnTimeSeries(
            constantTimeSeries,
            IPredictionModel.PredictionHorizon.FUTURE.howFarInSamples
        )

        assertEquals(expected, result.resultPoint.second, 0.0001)
    }


    @Test
    fun `PAST_3_HOUR_TREND strategy calculates correctly for non-linear data`() {
        val strategy = EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND.strategy
        val expected = 128.5986845799698 //expected value for this specific case

        val result = strategy.runOnTimeSeries(
            timeSeries,
            IPredictionModel.PredictionHorizon.FUTURE.howFarInSamples
        )

        assertEquals(expected, result.resultPoint.second, 0.0001)
    }
}
