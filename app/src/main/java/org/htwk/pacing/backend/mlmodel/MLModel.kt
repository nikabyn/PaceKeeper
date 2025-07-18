package org.htwk.pacing.backend.mlmodel

import android.content.Context
import android.util.Log
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.mlmodel.MLModel.Companion.denormalize
import org.htwk.pacing.backend.mlmodel.MLModel.Companion.normalize
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class MLModel(context: Context) {
    // create tensor flow lite interpreter for model at TFLITE_MODEL_FILE_PATH
    private val interpreter: Interpreter = Interpreter(
        FileUtil.loadMappedFile(context, TFLITE_MODEL_FILE_PATH)
    )

    init {
        showTFLiteMetadata()
    }

    companion object {
        private const val TFLITE_MODEL_FILE_PATH = "hr_prediction_model/model.tflite"

        //useful for outside access when preparing model input, getting output
        const val TIME_RESOLUTION_MINUTES = 10
        val TIME_UNIT_10_MINUTES = 10.minutes
        const val INPUT_DAYS: Int = 2
        const val INPUT_SIZE: Int = INPUT_DAYS * 24 * 6 // = 2 days in 10min timesteps = 288
        const val OUTPUT_SIZE: Int = 6 * 6 // 6 hours in 30 minutes timesteps = 36

        //doesn't need to be visible to outside for now
        private const val FEATURE_COUNT: Int = 6

        /** output of training script, it calculated the following generic stochastic properties
         * from the fitbit multi-person dataset
         * 2025-07-16 13:44:32,320 INFO: Mean HR: 79.76, Std HR: 18.73
         */

        const val HR_MEAN: Float = 79.76f
        const val HR_STANDARD_DEVIATION: Float = 18.73f

        /**
         * Normalizes the input data, returns the normalized form and the stochastic properties for
         * later denormalization
         * @param input The input data to be normalized.
         * @see StochasticProperties
         * @see denormalize
         */

        fun normalize(input: FloatArray): FloatArray {
            val normalized =
                input.map { (it - HR_MEAN) / HR_STANDARD_DEVIATION }.toFloatArray()
            return normalized
        }

        /**
         * Denormalizes the input data, returns the denormalized form
         * @param input The input data to be denormalized.
         * @return The denormalized data.
         * @see normalize
         */
        fun denormalize(
            input: FloatArray
        ): FloatArray {
            return input.map { x -> x * HR_STANDARD_DEVIATION + HR_MEAN }
                .toFloatArray()
        }
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

    //TODO: how to deal with calculations happening in between the 10min steps ?
    /**
     * Creates a [FloatBuffer] containing the normalized input heart rate data combined with
     * first and second order derivatives and cyclical time features.
     *
     * The buffer is structured to be directly usable as input for the LiteRT model.
     * Each input data point is augmented with its corresponding cyclical time representation.
     *
     * @param inputHeartRateNormalized The normalized heart rate data as a [FloatArray].
     * @param endTime The [Instant] marking the end of the input data period. Time features are calculated relative to this point.
     * @return A [FloatBuffer] ready for model inference, containing the combined heart rate and time features.
     */
    fun createInputBufferWithTimeFeaturesAndDerivatives(
        inputHeartRateNormalized: FloatArray,
        inputHasDataMask: BooleanArray,
        endTime: Instant
    ): FloatBuffer {
        val inputBuffer = FloatBuffer.allocate(INPUT_SIZE * FEATURE_COUNT)

        for (i in 0 until INPUT_SIZE) {
            //for (i in 0 until FEATURE_COUNT) inputBuffer.put(1.0f)
            //continue
            inputBuffer.put(inputHeartRateNormalized[i])

            val encodedHasData = if (inputHasDataMask[i]) 1.0f else 0.0f
            inputBuffer.put(encodedHasData)

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
     * 3. **Time Feature Encoding**: Augments the normalized heart rate data with cyclical time features
     *    (sine/cosine of time of day and day of week) based on the provided `endTime`.
     * 4. **Inference**: Runs the TensorFlow Lite model with the prepared input buffer.
     * 5. **Denormalization**: Denormalizes the model's output using the stored mean and standard deviation.
     *
     * Also, we work with FloatBuffer here since that's what TFLite expects. Using FloatArray would
     * require to much conversion boilerplate.
     *
     * @param inputHeartRate A [FloatArray] representing historical heart rate data. The size must be equal to `INPUT_SIZE`.
     * @param inputHasDataMask [BooleanArray] representing whether data existed at a certain timepoint
     * @param endTime An [Instant] representing the timestamp of the last data point in the `input` array.
     * @return A [FloatArray] containing the predicted future heart rate values. The size will be equal to `OUTPUT_SIZE`.
     */
    fun predict(
        inputHeartRate: FloatArray,
        inputHasDataMask: BooleanArray,
        endTime: Instant
    ): FloatArray {

        if (inputHeartRate.size != INPUT_SIZE) throw Exception("Wrong ml Model input size")

        //normalize heart rate input, because model works in normalized heart rate space
        val normalized = normalize(inputHeartRate)

        //encode time features
        val inputBuffer = createInputBufferWithTimeFeaturesAndDerivatives(
            inputHeartRateNormalized = normalized,
            inputHasDataMask = inputHasDataMask,
            endTime = endTime
        )

        //run inference and store in output buffer
        val outputBuffer = FloatBuffer.allocate(OUTPUT_SIZE)
        outputBuffer.rewind()
        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        //denormalize output and convert to return format (FloatArray instead of FloatBuffer)
        val output = FloatArray(OUTPUT_SIZE)
        outputBuffer.get(output)
        val denormalizedOutput = denormalize(output)
        return denormalizedOutput
    }

    /**
     * just for debugging, once helped find a mistake with .tflite file version that lead to an hour-long search
     * comparing the basic metrics of the .tflite model can help verify its (version) correctness
     */
    fun showTFLiteMetadata() {
        // Iterate over all input tensors
        val inputCount = interpreter.inputTensorCount
        for (i in 0 until inputCount) {
            val tensor = interpreter.getInputTensor(i)
            val name = tensor.name()
            val shape = tensor.shape().joinToString(prefix = "[", postfix = "]")
            val dtype = tensor.dataType()
            Log.i("MLModel", "Input #$i: name=$name, shape=$shape, dataType=$dtype")
        }
    }
}

