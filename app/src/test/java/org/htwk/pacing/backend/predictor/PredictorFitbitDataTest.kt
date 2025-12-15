package org.htwk.pacing.backend.predictor

import junit.framework.TestCase.assertEquals
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.database.Validation
import org.htwk.pacing.backend.helpers.plotTimeSeriesExtrapolationsWithPython
import org.htwk.pacing.backend.predictor.model.IPredictionModel
import org.htwk.pacing.backend.predictor.model.LinearCombinationPredictionModel.howFarInSamples
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPointTimeSeries.GenericTimedDataPoint
import org.htwk.pacing.backend.predictor.preprocessing.PIDComponent
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesMetric
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesSignalClass
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class PredictorFitbitDataTest {

    private lateinit var heartRateEntries: List<HeartRateEntry>
    private lateinit var distanceEntries: List<DistanceEntry>
    private lateinit var timeSeriesStart: Instant
    private lateinit var timeSeriesEnd: Instant

    @Before
    fun setUp() {
        fun loadHeartRateEntriesFromCSV(): List<HeartRateEntry> {
            val firstEntryTime = Instant.parse("2025-10-29T18:22:16Z")
            val file = File("src/test/resources/heart_rate_test_data.csv")
            return file.readLines()
                .drop(1) //skip header
                .map { line ->
                    val (minutes, bpm) = line.split(',').map { it.trim() }
                    HeartRateEntry(firstEntryTime + minutes.toLong().minutes, bpm.toLong())
                }
        }

        fun loadDistanceEntriesFromCSV(): List<DistanceEntry> {
            val firstEntryTime = Instant.parse("2025-09-30T16:58:00Z")
            val file = File("src/test/resources/distance_test_data.csv")
            return file.readLines()
                .drop(1) //skip header
                .map { line ->
                    val (minutes, distanceMeters) = line.split(',').map { it.trim() }
                    val timeEnd = firstEntryTime + minutes.toLong().minutes
                    DistanceEntry(
                        start = timeEnd - 1.minutes,
                        end = timeEnd,
                        Length(distanceMeters.toDouble())
                    )
                }
        }

        heartRateEntries = loadHeartRateEntriesFromCSV()
        distanceEntries = loadDistanceEntriesFromCSV()

        val earliestEntryTime = minOf(heartRateEntries.first().time, distanceEntries.first().start)
        val latestEntryTime = maxOf(heartRateEntries.last().time, distanceEntries.last().start)

        timeSeriesStart = earliestEntryTime
        timeSeriesEnd = latestEntryTime + 5.minutes
    }

    @Ignore("only for manual validation, not to be run in pipeline")
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
    }

    @Ignore("only for manual validation, not to be run in pipeline")
    @Test
    fun trainPredictorOnRecords() {
        val multiTimeSeriesEntries = Predictor.MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = timeSeriesStart,
            duration = timeSeriesEnd - timeSeriesStart,
            heartRate = heartRateEntries,
            distance = distanceEntries,
        )

        Predictor.train(
            multiTimeSeriesEntries,
            targetEnergyTimeSeriesEntries = multiTimeSeriesEntries.heartRate.map { it ->
                ValidatedEnergyLevelEntry(
                    it.time, Validation.Correct,
                    Percentage(it.bpm.toDouble() / 100.0)
                )
            },//TODO: fill
            fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)
        )

        val testWindowOffset = 0.days

        val testWindowStart = timeSeriesEnd - 2.days - testWindowOffset
        val testWindowEnd = timeSeriesEnd - 0.days - testWindowOffset

        val multiTimeSeriesEntriesTest = Predictor.MultiTimeSeriesEntries.createDefaultEmpty(
            timeStart = testWindowStart,
            duration = 2.days,
            heartRate = heartRateEntries.filter { it.time in testWindowStart..testWindowEnd },
            distance = distanceEntries.filter { it.end in testWindowStart..testWindowEnd }
        )

        val predictionResult = Predictor.predict(
            multiTimeSeriesEntries,
            fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)
        )

        println("-----")
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

        assertEquals(71.02011198570813, predictionResult.percentageFuture.toDouble() * 100.0, 0.1)
    }
}
