package org.htwk.pacing.backend.mlmodel

import android.content.Context
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.DataType
import java.nio.FloatBuffer
import java.time.Instant
import java.time.Duration
import java.time.ZoneId
import kotlin.math.*

class MLModel(context: Context) {
    private val interpreter: Interpreter

    init {
        // Load the TensorFlow Lite model
        val model = FileUtil.loadMappedFile(context, "hr_prediction_model/model.tflite");
        interpreter = Interpreter(model)
    }

    private fun encodeTimeFeatures(instant: Instant, zone: ZoneId = ZoneId.systemDefault()): Pair<Pair<Double, Double>, Pair<Double, Double>> {
        val zonedDateTime = instant.atZone(zone)

        val secondsInDay = 24 * 60 * 60
        val secondsInWeek = 7 * secondsInDay

        val secondsSinceMidnight = zonedDateTime.toLocalTime().toSecondOfDay()
        val dayOfWeekIndex = (zonedDateTime.dayOfWeek.value % 7) * secondsInDay + secondsSinceMidnight

        val dayRatio = secondsSinceMidnight.toDouble() / secondsInDay
        val weekRatio = dayOfWeekIndex.toDouble() / secondsInWeek

        val daySin = sin(2 * PI * dayRatio)
        val dayCos = cos(2 * PI * dayRatio)

        val weekSin = sin(2 * PI * weekRatio)
        val weekCos = cos(2 * PI * weekRatio)

        return (daySin to dayCos) to (weekSin to weekCos)
    }

    fun predict(input: FloatArray, endTime: Instant): FloatArray {
        val INPUT_DAYS = 2;
        val TIMESTEPS = INPUT_DAYS * 24 * 6; // 10 minute timesteps
        val FEATURE_COUNT = 5;
        assert(input.size == TIMESTEPS); // for now

        //TODO: how to deal with calculations happening in beetween the 10min steps

        val inputBuffer = FloatBuffer.allocate(TIMESTEPS * FEATURE_COUNT);
        for(i in 0..<TIMESTEPS) {
            inputBuffer.put(input[i])

            val timePoint = endTime.minus(Duration.ofMinutes((i * 10).toLong()));
            val (day, week) = encodeTimeFeatures(timePoint);
            val (day_sin, day_cos) = day;
            val (week_sin, week_cos) = week;

            inputBuffer.put(day_sin.toFloat())
            inputBuffer.put(day_cos.toFloat())
            inputBuffer.put(week_sin.toFloat())
            inputBuffer.put(week_cos.toFloat())
        }

        inputBuffer.rewind()

        val PRED_STEPS = 36
        val outputBuffer = FloatBuffer.allocate(PRED_STEPS)
        outputBuffer.rewind();

        //run inference
        interpreter.run(inputBuffer, outputBuffer);
        outputBuffer.rewind();

        //convert to return format (FloatArray instead of FloatBuffer)
        val predictions = FloatArray(PRED_STEPS);
        outputBuffer.get(predictions);

        //val inputTensor = TensorBuffer.createFixedSize(intArrayOf(1, input.size), DataType.FLOAT32)
        //val outputTensor = TensorBuffer.createFixedSize(intArrayOf(1, 10), DataType.FLOAT32)
        //interpreter.run(inputTensor.buffer, outputTensor.buffer.rewind())
        return predictions
    }
}