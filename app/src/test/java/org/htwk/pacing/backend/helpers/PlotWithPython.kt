package org.htwk.pacing.backend.helpers

import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator.EXTRAPOLATION_STRATEGY
import java.io.File
import java.util.concurrent.TimeUnit

fun plotMultiTimeSeriesEntriesWithPython(seriesData: Map<String, DoubleArray>) {
    if (seriesData.isEmpty()) {
        println("No data to plot.")
        return
    }

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

        dataFile = File.createTempFile("mtsd_data_", ".csv")
        dataFile.printWriter().use { out ->
            val headers = "index," + seriesData.keys.joinToString(",")
            out.println(headers)

            val maxLength = seriesData.values.firstOrNull()?.size ?: 0
            for (i in 0 until maxLength) {
                val rowItems = seriesData.values.map { doubleArray ->
                    doubleArray[i].toString()
                }
                out.println("$i,${rowItems.joinToString(",")}")
            }
        }
        println("Data dumped to temporary file: ${dataFile.absolutePath}")

        val command = mutableListOf("python", scriptFile.absolutePath, dataFile.absolutePath, "/home/u/git/pacing-app/ui/app/src/test/resources/exported/1/validated_energy_level.csv")

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
