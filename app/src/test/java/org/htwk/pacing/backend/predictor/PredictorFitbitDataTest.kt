package org.htwk.pacing.backend.predictor

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Length
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.minutes

class PredictorFitbitDataTest {

    @Serializable
    data class DistanceRecord(
        val dateTime: Instant,
        val value: String
    )

    @Serializable
    data class HeartRateRecord(
        val dateTime: Instant,
        val value: HeartRateValue
    )

    @Serializable
    data class HeartRateValue(
        val bpm: Int,
        val confidence: Int
    )

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private lateinit var recordsHeartRate: List<HeartRateRecord>
    private lateinit var recordsDistance: List<DistanceRecord>

    @Before
    fun setUp() {
        val folderHeartRate = File("src/test/resources/fitbit/heart_rate")
        recordsHeartRate = folderHeartRate.listFiles { f -> f.extension == "json" }
            ?.flatMap { file ->
                json.decodeFromString<List<HeartRateRecord>>(file.readText())
            }.orEmpty().sortedBy { it.dateTime }

        println("Loaded ${'$'}{recordsHeartRate.size} heart rate samples")
        println("First: ${'$'}{recordsHeartRate.firstOrNull()}")

        val folderDistance = File("src/test/resources/fitbit/distance")
        recordsDistance = folderDistance.listFiles { f -> f.extension == "json" }
            ?.flatMap { file ->
                json.decodeFromString<List<DistanceRecord>>(file.readText())
            }.orEmpty().sortedBy { it.dateTime }

        println("Loaded ${'$'}{recordsDistance.size} distance samples")
        println("First: ${'$'}{recordsDistance.firstOrNull()}")
    }

    @Test
    fun nonEmptyHeartRateRecords() {
        assert(recordsHeartRate.isNotEmpty())
    }

    @Test
    fun nonEmptyDistanceRecords() {
        assert(recordsDistance.isNotEmpty())
    }

    @Test
    fun trainPredictorOnRecords() {
        val predictor = Predictor()

        //convert the records to listOf<HeartRateEntry>
        val heartRateEntries = recordsHeartRate.map { record ->
            HeartRateEntry(record.dateTime, record.value.bpm.toLong())
        }

        val distanceEntries = recordsDistance.map { record ->
            DistanceEntry(
                start = record.dateTime,
                end = record.dateTime + 1.minutes,
                length = Length(lengthMeters = record.value.toDouble())
            )
        }

        val earliestEntryTime = minOf(heartRateEntries.first().time, distanceEntries.first().start)
        val latestEntryTime = minOf(heartRateEntries.last().time, distanceEntries.last().start)

        val multiTimeSeriesEntries = Predictor.MultiTimeSeriesEntries(
            timeStart = earliestEntryTime,
            duration = latestEntryTime - earliestEntryTime,
            heartRate = heartRateEntries,
            distance = distanceEntries
        )

        predictor.train(
            multiTimeSeriesEntries,
            fixedParameters = Predictor.FixedParameters(anaerobicThresholdBPM = 80.0)
        )
        //val predictions = predictor.predict(multiTimeSeriesEntries)
    }
}
