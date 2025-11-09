import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator.EXTRAPOLATION_STRATEGY
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

import java.io.File
import java.util.concurrent.TimeUnit

private fun plotWithPython(series: DoubleArray) {
    //1. Write the DoubleArray to a temporary CSV file
    val tempFile = File.createTempFile("/tmp/timeseries_data_", ".csv")
    tempFile.printWriter().use { out ->
        out.println("index,value")
        series.forEachIndexed { index, value ->
            out.println("$index,$value")
        }
    }
    println("Data dumped to temporary file: ${tempFile.absolutePath}")

    // 2. Run the Python script with the temp file path as an argument
    try {
        val process = ProcessBuilder("python", "/home/u/plot_data.py", tempFile.absolutePath)
            .redirectErrorStream(true) // Merges stderr with stdout
            .start()

        // Read the script's output for debugging purposes
        val scriptOutput = process.inputStream.bufferedReader().readText()
        process.waitFor(1, TimeUnit.MINUTES) // Wait for the script to finish

        if (scriptOutput.isNotBlank()) {
            println("Python script output:\n$scriptOutput")
        }

    } catch (e: Exception) {
        println("Failed to run plotting script. Is Python and Matplotlib installed?")
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        // 3. Clean up the temporary file
        tempFile.delete()
    }
}

class LinearExtrapolatorTest {

    private lateinit var timeSeries: DoubleArray
    private val stepsIntoFuture =
        (Predictor.PREDICTION_WINDOW_DURATION.inWholeSeconds / Predictor.TIME_SERIES_STEP_DURATION.inWholeSeconds)

    // Helper to calculate the expected result based on the user's formula
    private fun calculateExpectedValue(x0: Double, y0: Double, x1: Double, y1: Double): Double {
        if (x1 == x0) return y1 // Prevent division by zero, mirroring implicit behavior
        val slope = (y1 - y0) / (x1 - x0)
        return y1 + slope * (x1 + stepsIntoFuture)
    }

    @Before
    fun setUp() {
        // Create a more realistic, non-linear time series of 288 elements.
        // It generally trends upwards but has a dip in the middle.
        timeSeries = DoubleArray(288) { i ->
            val x = i / 288.0 // Normalize i to 0.0-1.0
            // A base linear trend plus a sine wave to make it go up and down
            (i * 0.8) + (kotlin.math.sin(x * 5.5 * kotlin.math.PI) * 20) + (kotlin.math.sin(x * 3.3 * kotlin.math.PI) * 36) + 50
        }
    }

    @Test
    fun `visualize the non-linear timeSeries using an external script`() {
        println("Preparing to plot time series data...")

        plotWithPython(timeSeries)

        // ASSERT
        // This test's primary purpose is visualization, not assertion.
        // But we add a trivial assertion to make the test runner happy.
        assertEquals(288, timeSeries.size)
        println("Plotting finished.")
    }

    @Test
    fun `multipleExtrapolate returns the correct, pinned results for a known non-linear timeSeries`() {
        // ARRANGE
        // This "golden result set" is calculated and pinned for the specific non-linear timeSeries.
        // If any underlying logic changes, these tests will fail, providing a strong regression guard.
        val expectedResults = mapOf(
            EXTRAPOLATION_STRATEGY.NOW_VS_30_MINUTES_AGO to 283.4398,
            EXTRAPOLATION_STRATEGY.NOW_VS_60_MINUTES_AGO to 283.4839,
            EXTRAPOLATION_STRATEGY.NOW_VS_90_MINUTES_AGO to 283.5133,
            EXTRAPOLATION_STRATEGY.NOW_VS_120_MINUTES_AGO to 283.5350,

            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_1_HOUR_AGO to 284.1578,
            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_2_HOURS_AGO to 284.1485,
            EXTRAPOLATION_STRATEGY.HOURLY_AVG_NOW_VS_3_HOURS_AGO to 284.1374,

            EXTRAPOLATION_STRATEGY.NOW_VS_1_HOUR_TREND to 284.0911,
            EXTRAPOLATION_STRATEGY.NOW_VS_3_HOUR_TREND to 283.9936,
            EXTRAPOLATION_STRATEGY.NOW_VS_6_HOUR_TREND to 283.8967,
            EXTRAPOLATION_STRATEGY.NOW_VS_12_HOUR_TREND to 283.9213,

            EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND to 283.8837,
            EXTRAPOLATION_STRATEGY.PAST_6_HOUR_TREND to 283.7845,
            EXTRAPOLATION_STRATEGY.PAST_12_HOUR_TREND to 284.2882,

            EXTRAPOLATION_STRATEGY.LAST_HOUR_AVERAGE_VS_1_DAY_AGO to 285.8080,
            EXTRAPOLATION_STRATEGY.YESTERDAY_VS_TODAY to 284.8123
        )


        // ACT
        val result = LinearExtrapolator.multipleExtrapolate(timeSeries)


        plotWithPython(timeSeries)

        // ASSERT
        assertEquals(
            "The number of strategies tested should match the number of results.",
            expectedResults.size,
            result.extrapolations.size
        )

        // Check each individual result against the golden set
        for ((strategy, expectedValue) in expectedResults) {
            val actualValue = result.extrapolations[strategy]
            assertEquals(
                "Mismatch for strategy: $strategy",
                expectedValue,
                actualValue!!,
                0.0001 // Using a delta for floating-point comparisons
            )
        }
    }

    @Test
    fun `runOnTimeSeries with constant time series produces flat extrapolation`() {
        // ARRANGE
        // This test is still valuable as a sanity check for slope calculation.
        val constantTimeSeries = DoubleArray(288) { 100.0 }
        val strategy = EXTRAPOLATION_STRATEGY.PAST_6_HOUR_TREND.strategy
        val expected = 100.0 // With a flat line, slope is 0, so result should be y1 (which is 100)

        // ACT
        val result = strategy.runOnTimeSeries(constantTimeSeries)

        // ASSERT
        assertEquals(expected, result, 0.0001)
    }

    // This individual test can be kept for easier debugging of a single complex case.
    @Test
    fun `PAST_3_HOUR_TREND strategy calculates correctly for non-linear data`() {
        // ARRANGE
        val strategy = EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND.strategy
        val expected = 283.8837 // Pinned golden value for this specific case

        // ACT
        val result = strategy.runOnTimeSeries(timeSeries)

        // ASSERT
        assertEquals(expected, result, 0.0001)
    }
}