package org.htwk.pacing.backend.predictor

import junit.framework.TestCase.assertEquals
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.Padding
import kotlinx.datetime.format.char
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.helpers.plotTimeSeriesExtrapolationsWithPython
import org.htwk.pacing.backend.predictor.model.LinearExtrapolator
import org.htwk.pacing.backend.predictor.preprocessing.GenericTimedDataPoint
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.TimeSeriesMetric
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * Custom serializer for handling Fitbit's "MM/dd/yy HH:mm:ss" date format.
 */
object FitbitDateTimeSerializer : KSerializer<Instant> {
    // Define the expected format of the date string from the JSON
    private val format = LocalDateTime.Format {
        monthNumber(padding = Padding.NONE)
        char('/')
        dayOfMonth(padding = Padding.NONE)
        char('/')
        yearTwoDigits(2000) // Assumes a 2-digit year, with a base of 2000
        char(' ')
        hour()
        char(':')
        minute()
        char(':')
        second()
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FitbitInstant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        // We don't need to write back to this format for this test, but it's here for completeness
        // The format specifies a 2-digit year. `kotlinx-datetime` formats years with 4 digits by default,
        // so we need to provide a base year to correctly format it as 2 digits.
        // See https://github.com/Kotlin/kotlinx-datetime/issues/215
        val localDateTime = value.toLocalDateTime(TimeZone.UTC)
        encoder.encodeString(format.format(localDateTime))
    }

    override fun deserialize(decoder: Decoder): Instant {
        val string = decoder.decodeString()
        // Parse the string using our custom format and convert it to an Instant (assuming UTC)
        return format.parse(string).toInstant(TimeZone.UTC)
    }
}

class PredictorFitbitDataTest {

    @Serializable
    data class DistanceRecord(
        // Tell the compiler to use our custom serializer for this field
        @Serializable(with = FitbitDateTimeSerializer::class)
        val dateTime: Instant,
        val value: String
    )

    @Serializable
    data class HeartRateRecord(
        // Also apply the serializer here
        @Serializable(with = FitbitDateTimeSerializer::class)
        val dateTime: Instant,
        val value: HeartRateValue
    )

    @Serializable
    data class HeartRateValue(
        val bpm: Int,
        val confidence: Int
    )

    // --- UPDATE THE JSON INSTANCE ---
    private val json = Json {
        ignoreUnknownKeys = true
        // No longer need serializersModule if using @Serializable(with=...) directly
    }

    private lateinit var heartRateEntries: List<HeartRateEntry>
    private lateinit var distanceEntries: List<DistanceEntry>
    private lateinit var timeSeriesStart: Instant
    private lateinit var timeSeriesEnd: Instant

    @Before
    fun setUp() {
        val folderHeartRate = File("src/test/resources/fitbit/heart_rate")
        val recordsHeartRate = folderHeartRate.listFiles { f -> f.extension == "json" }
            ?.flatMap { file ->
                json.decodeFromString<List<HeartRateRecord>>(file.readText())
            }.orEmpty().sortedBy { it.dateTime }

        println("Loaded ${recordsHeartRate.size} heart rate samples")
        println("First: ${recordsHeartRate.firstOrNull()}")

        val folderDistance = File("src/test/resources/fitbit/distance")
        val recordsDistance = folderDistance.listFiles { f -> f.extension == "json" }
            ?.flatMap { file ->
                json.decodeFromString<List<DistanceRecord>>(file.readText())
            }.orEmpty().sortedBy { it.dateTime }

        println("Loaded ${recordsDistance.size} distance samples")
        println("First: ${recordsDistance.firstOrNull()}")

        heartRateEntries = recordsHeartRate.map { record ->
            HeartRateEntry(record.dateTime, record.value.bpm.toLong())
        }

        distanceEntries = recordsDistance.map { record ->
            DistanceEntry(
                start = record.dateTime,
                end = record.dateTime + 1.minutes,
                length = Length(lengthMeters = record.value.toDouble())
            )
        }

        // Add a check to prevent crash if lists are empty after a failed load
        if (heartRateEntries.isEmpty() || distanceEntries.isEmpty()) {
            // Fail the test explicitly if data loading failed
            throw IllegalStateException("Test data could not be loaded. Check JSON files and paths.")
        }

        val earliestEntryTime = minOf(heartRateEntries.first().time, distanceEntries.first().start)
        val latestEntryTime = maxOf(heartRateEntries.last().time, distanceEntries.last().start)

        timeSeriesStart = earliestEntryTime
        timeSeriesEnd = latestEntryTime + 5.minutes
    }

    @Test
    fun testExtrapolationsPlotWithRealData() {
        println("Preparing to plot time series data...")

        val derivedTimeSeries =
            IPreprocessor.DiscreteTimeSeriesResult.DiscretePID.from(
                proportionalInput =
                    TimeSeriesDiscretizer.discretizeTimeSeries(
                        IPreprocessor.SingleGenericTimeSeriesEntries(
                            timeStart = timeSeriesStart,
                            duration = timeSeriesEnd - timeSeriesStart,
                            metric = TimeSeriesMetric.HEART_RATE,
                            data = heartRateEntries.filter { it -> true || it.start > timeSeriesEnd - 2.days }
                                .map(::GenericTimedDataPoint)
                        )
                    )
            ).proportional

        val result = LinearExtrapolator.multipleExtrapolate(derivedTimeSeries)

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

    @Test
    fun trainPredictorOnRecords() {
        val predictor = Predictor()

        val multiTimeSeriesEntries = Predictor.MultiTimeSeriesEntries(
            timeStart = timeSeriesStart,
            duration = timeSeriesEnd - timeSeriesStart,
            heartRate = heartRateEntries,
            distance = distanceEntries
        )

        predictor.train(
            multiTimeSeriesEntries,
            fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)
        )

        val testWindowOffset = 0.days

        val testWindowStart = timeSeriesEnd - 2.days - testWindowOffset
        val testWindowEnd = timeSeriesEnd - 0.days - testWindowOffset

        val multiTimeSeriesEntriesTest = Predictor.MultiTimeSeriesEntries(
            timeStart = testWindowStart,
            duration = 2.days,
            heartRate = heartRateEntries.filter { it.time in testWindowStart..testWindowEnd },
            distance = distanceEntries.filter { it.end in testWindowStart..testWindowEnd },
        )

        val predictionResult = predictor.predict(
            multiTimeSeriesEntries,
            fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)
        )

        println("-----")
        println("prediction time:  ${predictionResult.time}")
        println("prediction value: ${predictionResult.percentage}")
        //expected 62.12140545973156

        //after adding initial value relative offset to integral, derivative: 61.80762600761695

        //after going from 3.hours+33.minutes to 3.hours+ 0.minutes for stepSize: 61.943445172629794
        //after improving syntax in generateFlattenedMultiExtrapolationResults:   61.943445172629794
        println("training done")

        assertEquals(62.12140545973156, predictionResult.percentage.toDouble() * 100.0, 0.1)
    }
}
