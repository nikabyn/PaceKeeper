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
import org.htwk.pacing.backend.database.SkinTemperatureEntry
import org.htwk.pacing.backend.database.SleepSessionEntry
import org.htwk.pacing.backend.database.SleepStage
import org.htwk.pacing.backend.database.SpeedEntry
import org.htwk.pacing.backend.database.StepsEntry
import org.htwk.pacing.backend.database.Temperature
import org.htwk.pacing.backend.database.TimedEntry
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.backend.database.Velocity
import org.htwk.pacing.backend.helpers.plotMultiTimeSeriesEntriesWithPython
import org.htwk.pacing.backend.predictor.model.evaluateModel
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.PIDComponent
import org.htwk.pacing.backend.predictor.preprocessing.Preprocessor
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesMetric
import org.htwk.pacing.backend.predictor.stats.normalize
import org.htwk.pacing.ui.math.centeredMovingAverage
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.junit.Test
import java.io.File
import kotlin.let
import kotlin.time.Duration.Companion.days

class PredictorFitbitDataTest {
    val fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)

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
                    if(time < Instant.parse("2025-12-19T10:28:18.059Z") + 0.days) return@mapNotNull null
                    if(time !in minTime..maxTime) return@mapNotNull null
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
                    readCSVFileEntries(file, Instant.DISTANT_PAST, Instant.DISTANT_FUTURE) { parts ->
                        val time = Instant.parse(parts[0].trim())
                        val validation = parts[1].trim()
                        val percent = parts[2].trim().removeSuffix("%").toDouble()
                        ValidatedEnergyLevelEntry(time, Validation.valueOf(validation), Percentage(percent / 100.0))
                    }
                } ?: emptyList()

            val minTime = validatedEnergyEntries.minBy{it.time}.time
            val maxTime = validatedEnergyEntries.maxBy{it.time}.time

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



            val allLists: List<List<TimedEntry>> = listOf(
                //heartRate, distance, elevationGained, skinTemperature, heartRate,
                //oxygenSaturation, steps, speed, sleepSession
                validatedEnergyEntries
            )

            var earliestEntryTime = allLists
                .mapNotNull { it.minByOrNull { entry -> entry.end }?.end }
                .minOrNull()

            return Pair(Predictor.MultiTimeSeriesEntries(
                timeStart = earliestEntryTime!!,
                duration = maxTime - minTime,
                distance = distance,
                elevationGained = elevationGained,
                heartRate = heartRate,
                heartRateVariability = heartRateVariability,
                oxygenSaturation = oxygenSaturation,
                skinTemperature = skinTemperature,
                sleepSession = sleepSession,
                speed = speed,
                steps = steps,
            ), validatedEnergyEntries)
        }
    }

    val dataFromCSV = CSVHelper().readMultiCSV("src/test/resources/exported/2/")
    val multiTimeSeriesEntries = dataFromCSV.first
    val validatedEnergyLevelEntries = dataFromCSV.second

    val multiTimeSeriesDiscrete = Preprocessor.run(multiTimeSeriesEntries, fixedParameters)
    val targetTimeSeries = generateDiscreteTargetSeries(multiTimeSeriesEntries.timeStart, multiTimeSeriesEntries.duration, validatedEnergyLevelEntries, multiTimeSeriesDiscrete.stepCount())
    
    fun plotMTSD(mtsd: MultiTimeSeriesDiscrete) {
        plotMultiTimeSeriesEntriesWithPython(TimeSeriesMetric.entries
            .filter{
                true
            }
            .associate { it ->
                it.name to mtsd.getMutableRow(
                    MultiTimeSeriesDiscrete.FeatureID(
                        it,
                        PIDComponent.PROPORTIONAL
                    )
                ).let {
                    var discreteProportionalNormalized = it
                    discreteProportionalNormalized.normalize() //normalize for plotting, we dont care about relative amplitude here
                    discreteProportionalNormalized.toDoubleArray()
                }
            })
    }

    //@Ignore("only for manual validation, not to be run in pipeline")
    @Test
    fun testPlotDatasetFromCSV(){
        println(multiTimeSeriesEntries.heartRate.size)
        plotMTSD(multiTimeSeriesDiscrete)
    }

    /*@Ignore("only for manual validation, not to be run in pipeline")
    @Test
    fun testExtrapolationsPlotWithRealData() {
        println("Preparing to plot time series data...")

        val metric = TimeSeriesMetric.HEART_RATE
        val pidComponent = PIDComponent.PROPORTIONAL

        val derivedTimeSeries =
            pidComponent.compute(
                TimeSeriesDiscretizer.discretizeTimeSeries(
                    GenericTimedDataPointTimeSeries(
                        timeStart = timeSeriesEnd - 2.days,
                        duration = 2.days,
                        isContinuous = metric.signalClass == TimeSeriesSignalClass.CONTINUOUS,
                        data = heartRateEntries.filter { it -> it.time in (timeSeriesEnd - 2.days)..timeSeriesEnd }
                            .map(::GenericTimedDataPoint)
                    )
                )
            )

        val result = LinearExtrapolator.multipleExtrapolate(
            mk.ndarray(derivedTimeSeries),
            IPredictionModel.PredictionHorizon.FUTURE.howFarInSamples
        )

        result.extrapolations.entries.forEach { (strategy, extrapolation) ->
            println("Strategy: $strategy")
            val extr = extrapolation
            println("First Point: ${extr.firstPoint}")
            println("Second Point: ${extr.secondPoint}")
            println("Result Point: ${extr.resultPoint}")
        }

        plotTimeSeriesExtrapolationsWithPython(derivedTimeSeries, result.extrapolations)

        println("Plotting finished.")
    }*/

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

    @Test
    fun differentialPredictionModelTest() {
        val predictions = evaluateModel(multiTimeSeriesDiscrete, targetTimeSeries)

        //assertEquals(predictions[0].size, multiTimeSeriesDiscrete.stepCount())

        val minLength = minOf(
            predictions[0].size,
            targetTimeSeries.values.size
        )

        plotMultiTimeSeriesEntriesWithPython(
            mapOf(
                /*"SLEEP" to multiTimeSeriesDiscrete.getMutableRow(MultiTimeSeriesDiscrete.FeatureID(
                    TimeSeriesMetric.SLEEP_SESSION, PIDComponent.PROPORTIONAL)).toDoubleArray()
                    .slice(0 until minLength).toDoubleArray(),*/
                "TARGET" to centeredMovingAverage(targetTimeSeries.values, window = 64).slice(0 until minLength).toDoubleArray(),
                "PREDICTION1" to predictions[0].slice(0 until minLength).toDoubleArray(),
                "PREDICTION2" to predictions[1].slice(0 until minLength).toDoubleArray(),
                "PREDICTION3" to predictions[2].slice(0 until minLength).toDoubleArray()
            )
        )

        //plotMTSD(multiTimeSeriesDiscrete)
    }

    //@Ignore("only for manual validation, not to be run in pipeline")
    @Test
    fun trainPredictorOnRecords() {
        /*val testWindowOffset = 0.days

        val testWindowStart = timeSeriesEnd - 2.days - testWindowOffset
        val testWindowEnd = timeSeriesEnd - 0.days - testWindowOffset

        val multiTimeSeriesEntriesTest = Predictor.MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = testWindowStart,
            duration = 2.days,
            heartRate = heartRateEntries.filter { it.time in testWindowStart..testWindowEnd },
            distance = distanceEntries.filter { it.end in testWindowStart..testWindowEnd }
        )*/

        /*var testSet1 = MultiTimeSeriesDiscrete.fromSubSlice(multiTimeSeriesDiscrete, 0, 288)
        //test that slicing was correct
        for(i in 0 until 288) {
            assertEquals(testSet1.allFeaturesAt(i), multiTimeSeriesDiscrete.allFeaturesAt(i))
        }*/


        //plotMTSD(testSet)

        //var testSet1 = MultiTimeSeriesDiscrete.fromSubSlice(multiTimeSeriesDiscrete, 0, 100)
        //plotMTSD(multiTimeSeriesDiscrete)

        //var predictions = DoubleArray(multiTimeSeriesDiscrete.stepCount()) {0.0}


        //Preprocessor.run(inputTimeSeries, fixedParameters)


        /*val predictions = (0..multiTimeSeriesDiscrete.stepCount() - 300).map { i ->
            val testSet = MultiTimeSeriesDiscrete.fromSubSlice(multiTimeSeriesDiscrete, 0 + i, 288 + i)
            val predictionResult = Predictor.predict(
                testSet
            )
            predictionResult.percentageNow.toDouble()
        }.toDoubleArray()*/

        //plotMTSD(multiTimeSeriesDiscrete)

        /*plotMultiTimeSeriesEntriesWithPython(
            mapOf(
                "validated_energy_level" to multiTimeSeriesDiscrete.getMutableRow(
                    MultiTimeSeriesDiscrete.FeatureID(
                        TimeSeriesMetric.VALIDATED_ENERGY_LEVEL,
                        PIDComponent.PROPORTIONAL
                    )
                ).toDoubleArray()

                ""
            )
        )*/

        /*println("-----")
        println("prediction time:  ${predictionResult.time}")
        println("prediction value: ${predictionResult.percentageFuture}")
        println("prediction value now: ${predictionResult.percentageNow}")
        //expected 62.12140545973156

        //after adding initial value relative offset to integral, derivative: 61.80762600761695

        //after going from 3.hours+33.minutes to 3.hours+ 0.minutes for stepSize: 61.943445172629794
        //after improving syntax in generateFlattenedMultiExtrapolationResults:   61.943445172629794
        //after adding offset for discreteIntegral i forgot earlier:              61.943445172629794
        //after removing erroneous offset for DiscretePID.derivative:            62.214037264119625 (we pass again yay)
        //after adding new, more efficient training implementation:               66.11579001334712
        //after adding downsampled csv export/reload:                             70.98160649015591
        //after adding averaging for csv downsampling:                            70.94812981216073
        println("training done")

        //assertEquals(71.02011198570813, predictionResult.percentageFuture.toDouble() * 100.0, 0.1)
        //assertEquals(83.74639384260114, predictionResult.percentageFuture.toDouble() * 100.0, 0.1)
        assertEquals(84.44875652118324, predictionResult.percentageFuture.toDouble() * 100.0, 0.1)*/
    }
}
