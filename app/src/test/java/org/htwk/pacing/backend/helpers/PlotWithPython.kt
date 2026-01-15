package org.htwk.pacing.backend.helpers

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.htwk.pacing.backend.database.*
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator.EXTRAPOLATION_STRATEGY
import java.io.File
import java.util.concurrent.TimeUnit

fun plotMultiTimeSeriesEntriesWithPython(mtse: Predictor.MultiTimeSeriesEntries, validatedEnergy: List<ValidatedEnergyLevelEntry>) {
    val json = Json { prettyPrint = true }

    val seriesMap = mutableMapOf<String, List<Map<String, String>>>()

    fun <T : TimedEntry> addSeries(name: String, entries: List<T>) {
        if (entries.isEmpty()) return
        seriesMap[name] = entries.map {
            val value = when (it) {
                is DistanceEntry -> it.length.inMeters()
                is ElevationGainedEntry -> it.length.inMeters()
                is HeartRateEntry -> it.bpm
                is HeartRateVariabilityEntry -> it.variability
                is OxygenSaturationEntry -> it.percentage.toDouble()
                is SkinTemperatureEntry -> it.temperature.inCelsius()
                is SpeedEntry -> it.velocity.inMetersPerSecond()
                is StepsEntry -> it.count
                is SleepSessionEntry -> it.stage.ordinal
                is ValidatedEnergyLevelEntry -> it.percentage.toDouble()
                else -> require(false){"unknown type ${it::class.simpleName}"}
            }
            mapOf("time" to it.end.toString(), "value" to value.toString())
        }
    }

    addSeries("distance", mtse.distance)
    addSeries("elevationGained", mtse.elevationGained)
    addSeries("heartRate", mtse.heartRate)
    addSeries("heartRateVariability", mtse.heartRateVariability)
    addSeries("oxygenSaturation", mtse.oxygenSaturation)
    addSeries("skinTemperature", mtse.skinTemperature)
    addSeries("sleepSession", mtse.sleepSession)
    addSeries("speed", mtse.speed)
    addSeries("steps", mtse.steps)
    addSeries("validatedEnergy", validatedEnergy)

    val jsonData = json.encodeToString(seriesMap)

    var scriptFile: File? = null
    var dataFile: File? = null
    try {
        val scriptUrl = {}.javaClass.classLoader?.getResource("plot_mtse.py")
        if (scriptUrl == null) {
            println("ERROR: Could not find 'plot_mtse.py' in src/test/resources.")
            return
        }

        scriptFile = File.createTempFile("plot_script_", ".py")
        scriptFile.outputStream().use { fileOut ->
            scriptUrl.openStream().use { resourceIn ->
                resourceIn.copyTo(fileOut)
            }
        }

        dataFile = File.createTempFile("mtse_data_", ".json")
        dataFile.writeText(jsonData)
        println("Data dumped to temporary file: ${dataFile.absolutePath}")

        val command = mutableListOf("python", scriptFile.absolutePath, dataFile.absolutePath)

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
        dataFile?.delete()
        scriptFile?.delete()
    }
}

fun

plotTimeSeriesExtrapolationsWithPython(
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
        val scriptUrl = {}.javaClass.classLoader?.getResource("plot_extrapolations.py")
        if (scriptUrl == null) {
            println("ERROR: Could not find 'plot_extrapolations.py' in src/test/resources.")
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
