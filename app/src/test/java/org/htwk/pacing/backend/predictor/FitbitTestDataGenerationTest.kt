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

class FitbitTestDataGenerationTest {
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
    }

    private val minEntryDistanceForExport = 10.minutes

    fun exportHeartRateEntriesToCSV() {
        val file = File("src/test/resources/heart_rate_test_data.csv")
        val earliestEntryTime = heartRateEntries.first().time
        var lastTimeTaken = Instant.fromEpochMilliseconds(0)

        var currentSumBPM = 0.0
        var currentCountEntriesBPM = 0
        //export as csv
        file.printWriter().use { out ->
            out.println("minutes,bpm")
            for (entry in heartRateEntries) {
                currentSumBPM += entry.bpm
                currentCountEntriesBPM++
                if (entry.time - lastTimeTaken > minEntryDistanceForExport) {
                    val minutes = (entry.time - earliestEntryTime).inWholeMinutes
                    val bpmAverage = currentSumBPM / currentCountEntriesBPM.toDouble()
                    out.println("$minutes,${bpmAverage.toLong()}")
                    lastTimeTaken = entry.time
                    currentSumBPM = 0.0
                    currentCountEntriesBPM = 0
                }
            }
        }
    }

    fun exportDistanceEntriesToCSV() {
        val file = File("src/test/resources/distance_test_data.csv")
        val earliestEntryTime = distanceEntries.first().start
        var lastTimeTaken = Instant.fromEpochMilliseconds(0)

        var currentDistanceMeters = 0.0
        //export as csv
        file.printWriter().use { out ->
            out.println("minutes,distanceMeters")
            for (entry in distanceEntries) {
                currentDistanceMeters += entry.length.inMeters()
                if (entry.end - lastTimeTaken > minEntryDistanceForExport) {
                    val minutes = (entry.end - earliestEntryTime).inWholeMinutes
                    out.println("$minutes,${currentDistanceMeters}")
                    lastTimeTaken = entry.end
                    currentDistanceMeters = 0.0
                }
            }
        }
    }

    @Test
    fun testExportCSV() {
        exportHeartRateEntriesToCSV()
        exportDistanceEntriesToCSV()
    }
}
