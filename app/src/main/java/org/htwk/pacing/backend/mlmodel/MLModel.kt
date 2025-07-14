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
import kotlin.time.Duration.Companion.minutes

class MLModel(context: Context) {
    private val interpreter: Interpreter

    init {
        // Load the TensorFlow Lite model
        val model = FileUtil.loadMappedFile(context, "hr_prediction_model/model.tflite");
        interpreter = Interpreter(model)
    }

    companion object {
        const val TIME_RESOLUTION_MINUTES = 10
        const val INPUT_DAYS : Int = 2;
        const val INPUT_SIZE : Int = INPUT_DAYS * 24 * 6; // = 2 days in 10min timestept = 288
        private const val OUTPUT_SIZE : Int = 6 * 6 // 6 hours in 30 minutes timesteps = 36
        private const val FEATURE_COUNT : Int = 5;
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

    fun predict(input: FloatArray, endTime: Instant ): FloatArray {
        assert(input.size == INPUT_SIZE); // for now, catch wrong usage

        //normalize input array
        val mean = input.average().toFloat()
        val standardDeviation = input.map { x -> (x - mean).pow(2) }.average().pow(0.5).toFloat()
        val normalized = input.map { x -> (x - mean) / standardDeviation }.toFloatArray()

        val inputBuffer = FloatBuffer.allocate(INPUT_SIZE * FEATURE_COUNT);
        for(i in 0 until INPUT_SIZE) {
            inputBuffer.put(normalized[i])

            val timePoint = endTime.minus(Duration.ofMinutes((i * 10).toLong()));
            val (day, week) = encodeTimeFeatures(timePoint);
            val (daySin, dayCos) = day;
            val (weekSin, weekCos) = week;

            inputBuffer.put(daySin.toFloat())
            inputBuffer.put(dayCos.toFloat())
            inputBuffer.put(weekSin.toFloat())
            inputBuffer.put(weekCos.toFloat())
        }

        inputBuffer.rewind()

        val outputBuffer = FloatBuffer.allocate(OUTPUT_SIZE)
        outputBuffer.rewind();

        //run inference
        interpreter.run(inputBuffer, outputBuffer);
        outputBuffer.rewind();

        //convert to return format (FloatArray instead of FloatBuffer)
        val predictions = FloatArray(OUTPUT_SIZE);
        outputBuffer.get(predictions);

        //val inputTensor = TensorBuffer.createFixedSize(intArrayOf(1, input.size), DataType.FLOAT32)
        //val outputTensor = TensorBuffer.createFixedSize(intArrayOf(1, 10), DataType.FLOAT32)
        //interpreter.run(inputTensor.buffer, outputTensor.buffer.rewind())

        //return denormalized output
        val denormalized = predictions.map { x -> x * standardDeviation + mean }.toFloatArray()
        return denormalized
    }
}