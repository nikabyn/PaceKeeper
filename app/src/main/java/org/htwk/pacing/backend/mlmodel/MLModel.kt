package org.htwk.pacing.backend.mlmodel

import android.content.Context
import kotlinx.datetime.Instant
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class MLModel(context: Context) {
    // create tensor flow lite interpreter for model at TFLITE_MODEL_FILE_PATH
    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, TFLITE_MODEL_FILE_PATH)
    )

    companion object {
        private const val TFLITE_MODEL_FILE_PATH = "hr_prediction_model/model.tflite"

        //useful for outside access when preparing model input, getting output
        const val TIME_RESOLUTION_MINUTES = 10
        val TIME_UNIT_10_MINUTES = 10.minutes
        const val INPUT_DAYS: Int = 2
        const val INPUT_SIZE: Int = INPUT_DAYS * 24 * 6 // = 2 days in 10min timesteps = 288
        const val OUTPUT_SIZE: Int = 6 * 6 // 6 hours in 30 minutes timesteps = 36

        //doesn't need to be visible to outside for now
        private const val FEATURE_COUNT: Int = 5
    }

    /**
     * Represents a point in time using cyclical features.
     *
     * This is used to encode a timestamp into a suitable format our machine learning model, which
     * benefits from understanding the cyclical nature of time (e.g., time of day, day of week)
     * and the heart rates relation to that cyclical nature.
     * It captures the sine and cosine components of the time within a day and within a week.
     *
     * @property daySin The sine component of the time of day (24-hour cycle).
     * @property dayCos The cosine component of the time of day (24-hour cycle).
     * @property weekSin The sine component of the day of the week (7-day cycle).
     * @property weekCos The cosine component of the day of the week (7-day cycle).
     * @see encodeTimeToCyclicalFeature
     */
    data class CyclicalTimepointRepresentation(
        val daySin: Double,
        val dayCos: Double,
        val weekSin: Double,
        val weekCos: Double
    )

    /**
     * Encodes a given [Instant] into cyclical time features (sine and cosine transformations)
     * for both the time of day and the day of the week.
     *
     * This is commonly used in machine learning to represent time in a way that captures its cyclical nature
     * (e.g., 23:59 is close to 00:01).
     *
     * @param instant The [Instant] to encode.
     * @param zone The [ZoneId] to use for converting the [Instant] to a local date and time. Defaults to the system default.
     * @return A [Pair] containing two other [Pair]s:
     *         - The first inner [Pair] holds the (sine, cosine) for the time of day.
     *         - The second inner [Pair] holds the (sine, cosine) for the day of the week.
     * @see CyclicalTimepointRepresentation
     */
    fun encodeTimeToCyclicalFeature(
        instant: Instant
    ): CyclicalTimepointRepresentation {
        val secondsInDay = 1.days.inWholeSeconds
        val secondsInWeek = 7.days.inWholeSeconds

        val secondsSinceMidnight = instant.epochSeconds % secondsInDay
        val secondsSinceWeekBegin = instant.epochSeconds % secondsInWeek

        val dayRatio = secondsSinceMidnight.toDouble() / secondsInDay
        val weekRatio = secondsSinceWeekBegin.toDouble() / secondsInWeek

        return CyclicalTimepointRepresentation(
            daySin = sin(2 * PI * dayRatio),
            dayCos = cos(2 * PI * dayRatio),
            weekSin = sin(2 * PI * weekRatio),
            weekCos = cos(2 * PI * weekRatio)
        )
    }

    /**
     * Represents the stochastic properties (mean and standard deviation) of a dataset.
     *
     * This data class is used to store the calculated mean and standard deviation,
     * which are essential for normalizing input and denormalizing output data in out ml model.
     * Because Normalization greatly improves model performance, the tflite model is trained on
     * normalized data.
     *
     * @property mean The average value of the dataset.
     * @property standardDeviation The measure of the amount of variation or dispersion of the dataset.
     * @see normalize
     * @see denormalize
     */
    data class StochasticProperties(
        val mean: Float,
        val standardDeviation: Float
    )

    /**
     * Normalizes the input data, returns the normalized form and the stochastic properties for
     * later denormalization
     * @param input The input data to be normalized.
     * @return A pair containing the normalized data and the stochastic properties.
     * @see StochasticProperties
     * @see denormalize
     */
    fun normalize(input: FloatArray): Pair<FloatArray, StochasticProperties> {
        val mean = input.average().toFloat()
        val standardDeviationNonSafe =
            input.map { x -> (x - mean).pow(2) }.average().pow(0.5).toFloat()
        val standardDeviationSafe =
            if (standardDeviationNonSafe == 0.0f) 0.000001f else standardDeviationNonSafe

        val normalized = input.map { x -> (x - mean) / standardDeviationSafe }.toFloatArray()
        return Pair(normalized, StochasticProperties(mean, standardDeviationSafe))
    }

    /**
     * Denormalizes the input data, returns the denormalized form
     * @param input The input data to be denormalized.
     * @param stochasticProperties The stochastic properties used for normalization.
     * @return The denormalized data.
     * @see normalize
     * @see StochasticProperties
     */
    fun denormalize(
        input: FloatArray,
        stochasticProperties: StochasticProperties
    ): FloatArray {
        return input.map { x -> x * stochasticProperties.standardDeviation + stochasticProperties.mean }
            .toFloatArray()
    }

    //TODO: how to deal with calculations happening in between the 10min steps
    //TODO: how to deal with calculations happening in between the 10min steps
    /**
     * Creates a [FloatBuffer] containing the normalized input heart rate data combined with
     * cyclical time features.
     *
     * The buffer is structured to be directly usable as input for the TensorFlow Lite model.
     * Each input data point is augmented with its corresponding cyclical time representation.
     *
     * @param inputNormalized The normalized heart rate data as a [FloatArray].
     * @param endTime The [Instant] marking the end of the input data period. Time features are calculated relative to this point.
     * @return A [FloatBuffer] ready for model inference, containing the combined heart rate and time features.
     */
   fun createInputBufferWithTimeFeatures(
        inputNormalized: FloatArray,
        endTime: Instant
    ): FloatBuffer {
        val inputBuffer = FloatBuffer.allocate(INPUT_SIZE * FEATURE_COUNT)

        for (i in 0 until INPUT_SIZE) {
            inputBuffer.put(inputNormalized[i])

            val timePoint = endTime - TIME_UNIT_10_MINUTES * i
            val cyclicalTime = encodeTimeToCyclicalFeature(timePoint)

            inputBuffer.put(cyclicalTime.daySin.toFloat())
            inputBuffer.put(cyclicalTime.dayCos.toFloat())
            inputBuffer.put(cyclicalTime.weekSin.toFloat())
            inputBuffer.put(cyclicalTime.weekCos.toFloat())
        }

        inputBuffer.rewind()

        return inputBuffer
    }

    /**
     * Predicts future heart rate values based on historical heart rate data and the end time of that data.
     *
     * The prediction process involves several steps:
     * 1. **Input Validation**: Checks if the input array size matches the expected `INPUT_SIZE`.
     * 2. **Normalization**: Normalizes the input heart rate data. The model is trained on normalized data.
     *    The mean and standard deviation used for normalization are stored for later denormalization of the output.
     * 3. **Time Feature Encoding**: Augments the normalized heart rate data with cyclical time features
     *    (sine/cosine of time of day and day of week) based on the provided `endTime`.
     * 4. **Inference**: Runs the TensorFlow Lite model with the prepared input buffer.
     * 5. **Denormalization**: Denormalizes the model's output using the stored mean and standard deviation.
     *
     * Also, we work with FloatBuffer here since that's what TFLite expects. Using FloatArray would
     * require to much conversion boilerplate.
     *
     * @param input A [FloatArray] representing historical heart rate data. The size must be equal to `INPUT_SIZE`.
     * @param endTime An [Instant] representing the timestamp of the last data point in the `input` array.
     * @return A [FloatArray] containing the predicted future heart rate values. The size will be equal to `OUTPUT_SIZE`.
     */
    fun predict(input: FloatArray, endTime: Instant): FloatArray {
        if (input.size != INPUT_SIZE) throw Exception("Wrong ml Model input size")

        //normalize heart rate input, because model works in normalized heart rate space
        val (normalized, stochasticProperties) = normalize(input)

        //encode time features
        val inputBuffer = createInputBufferWithTimeFeatures(normalized, endTime)

        //run inference and store in output buffer
        val outputBuffer = FloatBuffer.allocate(OUTPUT_SIZE)
        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        //denormalize output and convert to return format (FloatArray instead of FloatBuffer)
        val output = FloatArray(OUTPUT_SIZE)
        outputBuffer.get(output)
        val denormalizedOutput = denormalize(output, stochasticProperties)
        return return denormalizedOutput
    }
}
