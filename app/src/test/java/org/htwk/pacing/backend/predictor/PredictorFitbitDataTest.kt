package org.htwk.pacing.backend.predictor

import junit.framework.TestCase.assertEquals
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.ElevationGainedEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.HeartRateVariabilityEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.OxygenSaturationEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntry
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.backend.database.Velocity
import org.htwk.pacing.backend.helpers.plotMultiTimeSeriesEntriesWithPython
import org.htwk.pacing.backend.predictor.model.DifferentialPredictionModel
import org.htwk.pacing.backend.predictor.model.IPredictionModel
import org.htwk.pacing.backend.predictor.model.evaluateModel
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class PredictorFitbitDataTest {
    private fun cleanValidatedEnergyEntries(validatedEnergyLevelEntries: List<ValidatedEnergyLevelEntry>): List<ValidatedEnergyLevelEntry> {
        return validatedEnergyLevelEntries.sortedBy { it.time }
            .fold(mutableListOf<ValidatedEnergyLevelEntry>()) { acc, next ->
                val last = acc.lastOrNull()
                //if next entry is close to the current 'last' entry, average them
                if (last != null && (next.time - last.time) < 4.hours) {
                    acc[acc.lastIndex] = last.copy(
                        percentage = Percentage((last.percentage.toDouble() + next.percentage.toDouble()) / 2.0)
                    )
                } else {
                    acc.add(next)
                }
                acc
            }
    }

    class CSVHelper {
        fun <T> readCSVFileEntries(
            file: File,
            minTime: Instant,
            maxTime: Instant,
            entryGenerator: (List<String>) -> T
        ): List<T> {
            return file.readLines()
                .drop(1) // skip header
                .mapNotNull { line ->
                    val parts = line.split(",")
                    val time = Instant.parse(parts[0].trim())
                    if (time < Instant.parse("2025-12-19T10:28:18.059Z") + 4.days) return@mapNotNull null
                    if (time !in minTime..maxTime) return@mapNotNull null
                    try {
                        entryGenerator(parts)
                    } catch (e: Exception) {
                        println("Malformed line in ${file.name}: $line")
                        null
                    }
                }
        }

        fun readMultiCSV(path: String): Pair<Predictor.MultiTimeSeriesEntries, List<ValidatedEnergyLevelEntry>> {
            val directory = File(path)
            val csvFiles = directory
                .listFiles { file -> file.extension == "csv" }
                ?.associateBy { it.name }
                .orEmpty()

            // --- validated_energy_level.csv ---
            var validatedEnergyEntries =
                csvFiles["validated_energy_level.csv"]?.let { file ->
                    readCSVFileEntries(
                        file,
                        Instant.DISTANT_PAST,
                        Instant.DISTANT_FUTURE
                    ) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val validation = parts[1].trim()
                        val percent = parts[2].trim().removeSuffix("%").toDouble()
                        ValidatedEnergyLevelEntry(
                            time,
                            Validation.valueOf(validation),
                            Percentage(percent / 100.0)
                        )
                    }
                } ?: emptyList()

            val minTime = validatedEnergyEntries.minBy { it.time }.time
            val maxTime = validatedEnergyEntries.maxBy { it.time }.time

            // --- distance.csv ---
            val distance =
                csvFiles["distance.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val meters = parts[1].trim().toDouble()
                        DistanceEntry(time, time, Length(meters))
                    }
                } ?: emptyList()

            // --- elevation.csv ---
            val elevationGained =
                csvFiles["elevation.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val meters = parts[1].trim().toDouble()
                        ElevationGainedEntry(time, time, Length(meters))
                    }
                } ?: emptyList()

            // --- heart_rate.csv ---
            val heartRate =
                csvFiles["heart_rate.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val bpm = parts[1].trim().toLong()
                        HeartRateEntry(time, bpm)
                    }
                } ?: emptyList()

            // --- heart_rate_variability.csv ---
            val heartRateVariability =
                csvFiles["heart_rate_variability.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val value = parts[1].trim().toDouble()
                        HeartRateVariabilityEntry(time, value)
                    }
                } ?: emptyList()

            // --- manual_symptom.csv ---
            /*val manualSymptomEntries =
                csvFiles["manual_symptom.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val symptom = parts[1].trim()
                        ManualSymptomEntry(time, symptom)
                    }
                } ?: emptyList()*/

            // --- menstruation.csv ---
            /*val menstruationEntries =
                csvFiles["menstruation.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val value = parts[1].trim()
                        MenstruationPeriodEntry(time, time, value)
                    }
                } ?: emptyList()*/

            // --- oxygen_saturation.csv ---
            val oxygenSaturation =
                csvFiles["oxygen_saturation.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val percent = parts[1].trim().removeSuffix("%").toDouble()
                        OxygenSaturationEntry(time, Percentage(percent / 100.0))
                    }
                } ?: emptyList()

            // --- predicted_energy_level.csv ---
            /*val predictedEnergyEntries =
                csvFiles["predicted_energy_level.csv"]?.let { file ->
                    readCSVFileEntries(file) { parts ->
                        val timeNow = Instant.parse(parts[0].trim())
                        val percentageNow = parts[1].trim().removeSuffix("%").toDouble()
                        val timeFuture = Instant.parse(parts[2].trim())
                        val percentageFuture = parts[3].trim().removeSuffix("%").toDouble()
                        PredictedEnergyLevelEntry(
                            timeNow,
                            Percentage(percentageNow),
                            timeFuture,
                            Percentage(percentageFuture)
                        )
                    }
                } ?: emptyList()*/

            // --- skin_temperature.csv ---
            val skinTemperature =
                csvFiles["skin_temperature.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val temp = parts[1].trim().toDouble()
                        SkinTemperatureEntry(time, Temperature(temp))
                    }
                } ?: emptyList()

            // --- sleep_sessions.csv ---
            val sleepSession =
                csvFiles["sleep_sessions.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val start = Instant.parse(parts[0].trim())
                        val end = Instant.fromEpochMilliseconds(parts[1].trim().toLong())
                        val stage = parts[2].trim()
                        SleepSessionEntry(start, end, SleepStage.valueOf(stage))
                    }
                } ?: emptyList()

            // --- speed.csv ---
            val speed =
                csvFiles["speed.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val speed = parts[1].trim().toDouble()
                        SpeedEntry(time, Velocity(speed))
                    }
                } ?: emptyList()

            // --- steps.csv ---
            val steps =
                csvFiles["steps.csv"]?.let { file ->
                    readCSVFileEntries(file, minTime, maxTime) { parts ->
                        val start = Instant.parse(parts[0].trim())
                        val end = Instant.fromEpochMilliseconds(parts[1].trim().toLong())
                        val count = parts[2].trim().toLong()
                        StepsEntry(start, end, count)
                    }
                } ?: emptyList()


            return Pair(
                Predictor.MultiTimeSeriesEntries(
                    timeStart = minTime,
                    duration = maxTime - minTime,
                    distance = distance,
                    elevationGained = elevationGained,
                    heartRate = heartRate.slice(heartRate.indices step 10),
                    heartRateVariability = heartRateVariability,
                    oxygenSaturation = oxygenSaturation,
                    skinTemperature = skinTemperature,
                    sleepSession = sleepSession,
                    speed = speed,
                    steps = steps,
                ), validatedEnergyEntries
            )

        }
    }

    val fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)

    val dataFromCSV = CSVHelper().readMultiCSV("src/test/resources/exported/2/")
    val multiTimeSeriesEntries = dataFromCSV.first
    val validatedEnergyLevelEntries = dataFromCSV.second

    val multiTimeSeriesDiscrete = Preprocessor.run(multiTimeSeriesEntries, fixedParameters)
    val targetTimeSeries = generateDiscreteTargetSeries(
        multiTimeSeriesEntries.timeStart,
        multiTimeSeriesEntries.duration,
        validatedEnergyLevelEntries,
        multiTimeSeriesDiscrete.stepCount()
    )

    @Ignore("only for manual validation, not to be run in pipeline")
    @Test
    fun testPlotDatasetFromCSV() {
        println(multiTimeSeriesEntries.heartRate.size)
        plotMultiTimeSeriesEntriesWithPython(
            mapOf(
                "DISTANCE" to multiTimeSeriesEntries.distance.map(::GenericTimedDataPoint),
                "ELEVATION_GAINED" to multiTimeSeriesEntries.elevationGained.map(::GenericTimedDataPoint),
                "HEART_RATE" to multiTimeSeriesEntries.heartRate.map(::GenericTimedDataPoint),
                "HEART_RATE_VARIABILITY" to multiTimeSeriesEntries.heartRateVariability.map(::GenericTimedDataPoint),
                "OXYGEN_SATURATION" to multiTimeSeriesEntries.oxygenSaturation.map(::GenericTimedDataPoint),
                "SKIN_TEMPERATURE" to multiTimeSeriesEntries.skinTemperature.map(::GenericTimedDataPoint),
                "SLEEP_SESSION" to multiTimeSeriesEntries.sleepSession.map(::GenericTimedDataPoint),
                "SPEED" to multiTimeSeriesEntries.speed.map(::GenericTimedDataPoint),
                "STEPS" to multiTimeSeriesEntries.steps.map(::GenericTimedDataPoint),
                "TARGET" to validatedEnergyLevelEntries.map { it ->
                    GenericTimedDataPoint(
                        it.time,
                        it.percentage.toDouble()
                    )
                }
            )
        )
    }

    @Test
    fun plotTimeshiftCorrelations() {
        /*var m = multiTimeSeriesDiscrete.getAllFeatureIDs().associateWith { id ->
            DoubleArray(400)
        }

        for(offs in 0 until 400) {
            println("time offset ${offs}")
            DifferentialPredictionModel.timeOffset = offs
            Predictor.train(
                multiTimeSeriesDiscrete,
                targetTimeSeries,
                fixedParameters
            )

            multiTimeSeriesDiscrete.getAllFeatureIDs().forEachIndexed { index, value: MultiTimeSeriesDiscrete.FeatureID ->
                m[value]!!.set(offs,
                    DifferentialPredictionModel.model!!.weights[index]
                )
            }
        }

        val m1 = m.keys.associate { id ->
            "${id.metric}-${
                id.component.toString().substring(0, 4)
            }" to m[id]!!
        }

        //TODO IDEA: CONVOLUTION by this graph, add convolved energy / 400 (divide by no of layers that affected a point)

        plotMultiTimeSeriesEntriesWithPython(
            m1
        )*/
    }

    // @Ignore("only for manual validation, not to be run in pipeline")
    @Test
    fun differentialPredictionModelTest() {
        val predictions = evaluateModel(multiTimeSeriesDiscrete, targetTimeSeries).toMutableList()

        assertEquals(multiTimeSeriesDiscrete.stepCount(), targetTimeSeries.values.size)
        assertEquals(predictions[0].size, multiTimeSeriesDiscrete.stepCount())


        val targ = targetTimeSeries.values

        fun toGenericTimedDataPointList(data: DoubleArray) = data.mapIndexed { index, value ->
            GenericTimedDataPoint(
                time = Instant.fromEpochSeconds(index.toLong()), value = value
            )
        }

        //assertEquals(574344646, predictions[0].contentHashCode())

        plotMultiTimeSeriesEntriesWithPython(
            mapOf(
                "TARGET" to toGenericTimedDataPointList(targ),
                "PREDICTION1" to toGenericTimedDataPointList(predictions[0]),
                "PREDICTION2" to toGenericTimedDataPointList(predictions[1]),
                "PREDICTION3" to toGenericTimedDataPointList(predictions[2]),
            )
        )
    }

    //@Ignore("only for manual validation, not to be run in pipeline")
    @Test
    fun differentialPredictionModelTestRawData() {
        //1. Get all raw data entries from the CSV helper
        val allEntries = dataFromCSV.first
        val allValidatedEnergy = dataFromCSV.second
        val windowDuration = Predictor.TIME_SERIES_DURATION
        val stepDuration = Predictor.TIME_SERIES_STEP_DURATION


        val overallStartTime = allEntries.timeStart
        val overallEndTime = overallStartTime + allEntries.duration - 1.hours

        Predictor.train(multiTimeSeriesDiscrete, targetTimeSeries)

        println(multiTimeSeriesDiscrete.timeStart)
        println(allEntries.timeStart)
        //iterate with a sliding window to generate predictions
        val predictions = mutableListOf<PredictedEnergyLevelEntry>()
        var currentWindowEnd = overallStartTime + 1.hours

        var i = 1

        var predictionResult1 = 0.3
        var predictionResult2 = 0.3

        val pred_predictor = mutableListOf<PredictedEnergyLevelEntry>()

        val featureHistory = mutableMapOf<String, MutableList<GenericTimedDataPoint>>()

        while (currentWindowEnd <= overallEndTime) {
            val currentWindowStart = overallStartTime

            // a. Create MultiTimeSeriesEntries for the current 2-day window
            val windowEntries = Predictor.MultiTimeSeriesEntries(
                timeStart = currentWindowStart,
                duration = currentWindowEnd - currentWindowStart,
                distance = allEntries.distance.filter { it.end in currentWindowStart..currentWindowEnd },
                elevationGained = allEntries.elevationGained.filter { it.end in currentWindowStart..currentWindowEnd },
                heartRate = allEntries.heartRate.filter { it.time in currentWindowStart..currentWindowEnd },
                heartRateVariability = allEntries.heartRateVariability.filter { it.time in currentWindowStart..currentWindowEnd },
                oxygenSaturation = allEntries.oxygenSaturation.filter { it.time in currentWindowStart..currentWindowEnd },
                skinTemperature = allEntries.skinTemperature.filter { it.time in currentWindowStart..currentWindowEnd },
                sleepSession = allEntries.sleepSession.filter { it.end in currentWindowStart..currentWindowEnd },
                speed = allEntries.speed.filter { it.time in currentWindowStart..currentWindowEnd },
                steps = allEntries.steps.filter { it.end in currentWindowStart..currentWindowEnd }
            )

            //preprocess this specific window's data
            val windowMTSD = Preprocessor.run(windowEntries, fixedParameters)

            /*val lastValidatedEnergyLevelEntryInWindow = validatedEnergyLevelEntries
                .filter { it.end in currentWindowStart..currentWindowEnd }.map{it.percentage.toDouble()}.average()*/
            val lastValidatedEnergyLevelEntryInWindow =
                validatedEnergyLevelEntries.filter { it.end in currentWindowStart..currentWindowEnd }
                    .maxByOrNull { it.time }

            println(lastValidatedEnergyLevelEntryInWindow)

            val lastTime = lastValidatedEnergyLevelEntryInWindow?.time ?: currentWindowStart
            val lastEnergy = lastValidatedEnergyLevelEntryInWindow?.percentage?.toDouble() ?: 0.5

            predictionResult1 += DifferentialPredictionModel.predict(
                input = multiTimeSeriesDiscrete,
                offset = ((currentWindowEnd - overallStartTime) / stepDuration).toInt(),
                horizon = IPredictionModel.PredictionHorizon.NOW
            )

            predictionResult2 += DifferentialPredictionModel.predict(
                input = windowMTSD,
                offset = windowMTSD.stepCount() - 1,
                horizon = IPredictionModel.PredictionHorizon.NOW
            )

            val entry =
                PredictedEnergyLevelEntry(
                    time = currentWindowEnd,
                    percentageNow = Percentage(predictionResult1),
                    timeFuture = currentWindowEnd,
                    percentageFuture = Percentage(predictionResult2)
                )
            predictions.add(entry)

            val pred3 = Predictor.predict(
                multiTimeSeriesDiscrete = windowMTSD,
                lastValidatedEnergy = lastEnergy,
                lastValidatedTime = lastTime,
                timeNow = currentWindowEnd
            )
            pred_predictor.add(pred3)

            windowMTSD.getAllFeatureIDs().forEach { featureID ->
                // Create a readable name like "HEART_RATE_SQUARED"
                val key = "${featureID.metric.name}_${featureID.component.name}"

                // Get value at the *current* (last) timestep
                val value = windowMTSD[featureID, windowMTSD.stepCount() - 1]

                // Add to history
                featureHistory.getOrPut(key) { mutableListOf() }
                    .add(GenericTimedDataPoint(currentWindowEnd, value))
            }

            i++

            //slide the window forward one step
            currentWindowEnd += stepDuration
        }

        //assertEquals(i, targetTimeSeries.values.size)

        val pred1 = predictions.map { it -> it.percentageNow.toDouble() }
            .toDoubleArray()//.discreteTrapezoidalIntegral(0.3)
        val pred2 = predictions.map { it -> it.percentageFuture.toDouble() }
            .toDoubleArray()//.discreteTrapezoidalIntegral(0.3)

        plotMultiTimeSeriesEntriesWithPython(
            mapOf(
                //"SLEEP1" to allEntries.sleepSession.map(::GenericTimedDataPoint),
                "VALIDATED" to allValidatedEnergy.map { it ->
                    GenericTimedDataPoint(
                        it.time,
                        it.percentage.toDouble()
                    )
                },
                //"SLEEP_LAST" to predictions.map{it -> GenericTimedDataPoint(it.time, it.percentageNow.toDouble()) },
                //"PREDICTED_GLOBAL_MTSD" to predictions.mapIndexed{index, value -> GenericTimedDataPoint(value.time, pred1[index.coerceAtMost(pred1.size - 1)]) },
                //"PREDICTED_LOCAL_MTSD" to predictions.mapIndexed{index, value -> GenericTimedDataPoint(value.time, pred2[index.coerceAtMost(pred2.size - 1)]) },
                "PREDICTED_NOW_PREDICTOR" to pred_predictor.mapIndexed { index, value ->
                    GenericTimedDataPoint(
                        value.time,
                        value.percentageNow.toDouble()
                    )
                },
                "PREDICTED_FUTURE_PREDICTOR" to pred_predictor.mapIndexed { index, value ->
                    GenericTimedDataPoint(
                        value.time + IPredictionModel.PredictionHorizon.FUTURE.howFar,
                        value.percentageFuture.toDouble()
                    )
                },

                )// + featureHistory
        )
    }
}
