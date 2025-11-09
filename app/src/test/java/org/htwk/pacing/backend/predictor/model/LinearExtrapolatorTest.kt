import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator.EXTRAPOLATION_STRATEGY
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

private fun plotWithPython(
    series: DoubleArray,
    extrapolations: Map<EXTRAPOLATION_STRATEGY, LinearExtrapolator.ExtrapolationLine> = emptyMap()
) {
    // 1. Write the time series data to a temporary CSV file
    val seriesFile = File.createTempFile("timeseries_data_", ".csv")
    seriesFile.printWriter().use { out ->
        out.println("index,value")
        series.forEachIndexed { index, value ->
            out.println("$index,$value")
        }
    }
    println("Data dumped to temporary file: ${seriesFile.absolutePath}")

    // 2. Write extrapolation lines to a second temporary file
    val extrapolationFile = if (extrapolations.isNotEmpty()) {
        val tempFile = File.createTempFile("extrapolation_data_", ".csv")
        tempFile.printWriter().use { out ->
            out.println("name,x1,y1,x2,y2,x_res,y_res")
            extrapolations.forEach { (name, line) ->
                out.println(
                    // Wrap name in quotes to handle special characters
                    "\"${name}\"," +
                            // The points for the trend line
                            "${line.firstPoint.first},${line.firstPoint.second}," +
                            "${line.secondPoint.first},${line.secondPoint.second}," +
                            "${line.resultPoint.first},${line.resultPoint.second}"
                )
            }
        }
        println("Extrapolation data dumped to: ${tempFile.absolutePath}")
        tempFile
    } else {
        null
    }

    // 3. Locate and execute the Python script from resources
    var scriptFile: File? = null
    try {
        // Load the script from the test resources folder
        val scriptUrl = {}.javaClass.classLoader?.getResource("plot_data.py")
        if (scriptUrl == null) {
            println("ERROR: Could not find 'plot_data.py' in src/test/resources.")
            return
        }

        // Create a temporary executable file from the resource URL
        scriptFile = File.createTempFile("plot_script_", ".py")
        scriptFile.deleteOnExit() // Ensure cleanup
        scriptFile.outputStream().use { fileOut ->
            scriptUrl.openStream().use { resourceIn ->
                resourceIn.copyTo(fileOut)
            }
        }

        val command = mutableListOf("python", scriptFile.absolutePath, seriesFile.absolutePath)
        extrapolationFile?.let { command.add(it.absolutePath) }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        val scriptOutput = process.inputStream.bufferedReader().readText()
        process.waitFor(1, TimeUnit.MINUTES)

        if (scriptOutput.isNotBlank()) {
            println("Python script output: $scriptOutput")
        }

    } catch (e: Exception) {
        println("Failed to run plotting script. Is Python and Matplotlib installed?")
        println("Error: ${e.message}")
        e.printStackTrace()
    } finally {
        // 4. Clean up the temporary files
        seriesFile.delete()
        extrapolationFile?.delete()
        scriptFile?.delete() // also cleans up our temporary script copy
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
            (i * 0.8) + (kotlin.math.sin(x * 5.5 * kotlin.math.PI) * 20) +
                    (kotlin.math.sin(x * 3.3 * kotlin.math.PI) * 36) +
                    (kotlin.math.cos(x * 9.7 * kotlin.math.PI) * 9) +
                    (kotlin.math.cos(x * 13.4 * kotlin.math.PI) * 7.6) +
                    (kotlin.math.sin(x * kotlin.math.sin(x * 1.3) * 29.4 * kotlin.math.PI) * 3.6) +
                    50
        }
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

        plotWithPython(timeSeries, result.extrapolations)

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

        // ACT
        val result = LinearExtrapolator.multipleExtrapolate(timeSeries)

        // ASSERT
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
        assertEquals(expected, result.resultPoint.second, 0.0001)
    }

    // This individual test can be kept for easier debugging of a single complex case.
    @Test
    fun `PAST_3_HOUR_TREND strategy calculates correctly for non-linear data`() {
        // ARRANGE
        val strategy = EXTRAPOLATION_STRATEGY.PAST_3_HOUR_TREND.strategy
        val expected = 190.95672 // Pinned golden value for this specific case

        // ACT
        val result = strategy.runOnTimeSeries(timeSeries)

        // ASSERT
        assertEquals(expected, result.resultPoint.second, 0.0001)
    }
}
