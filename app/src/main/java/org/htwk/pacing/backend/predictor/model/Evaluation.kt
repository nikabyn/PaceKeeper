package org.htwk.pacing.backend.predictor.model

import androidx.annotation.FloatRange
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer
import org.htwk.pacing.ui.math.centeredMovingAverage
import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral
import kotlin.math.pow

/**
 * Computes Mean Squared Error (MSE).
 *
 * MSE = average of (prediction - actual)^2
 */
fun meanSquaredError(predictions: DoubleArray, actual: DoubleArray): Double {
    require(predictions.size == actual.size) {
        "Predictions and actual arrays must have the same length"
    }

    return predictions.zip(actual)
        .map { (p, a) -> (p - a).pow(2) }
        .average()
}

/**
 * Computes R2 score (coefficient of determination).
 *
 * R2 = 1 - (SS_res / SS_tot)
 * SS_res = sum of squared residuals
 * SS_tot = total variance of actual values
 */
fun r2Score(predictions: DoubleArray, actual: DoubleArray): Double {
    require(predictions.size == actual.size) {
        "Predictions and actual arrays must have the same length"
    }

    val meanActual = actual.average()

    // Sum of squared residuals: how far predictions deviate from actual values
    val ssRes = predictions.zip(actual)
        .sumOf { (p, a) -> (a - p).pow(2) }

    // Total sum of squares: how far actual values deviate from their mean
    val ssTot = actual.sumOf { a -> (a - meanActual).pow(2) }

    return 1 - (ssRes / ssTot)
}

internal fun producePredictions(model: IPredictionModel, fullMTSD: MultiTimeSeriesDiscrete) : DoubleArray {
    val predictions = (0 until fullMTSD.stepCount() - Predictor.TIME_SERIES_SAMPLE_COUNT).map {
            i ->
        val testSet = MultiTimeSeriesDiscrete.fromSubSlice(fullMTSD, i, i + Predictor.TIME_SERIES_SAMPLE_COUNT)
        model.predict(
            testSet,
            IPredictionModel.PredictionHorizon.NOW
        )
    }.toDoubleArray()

    return predictions
}

fun trainingSplit(input: MultiTimeSeriesDiscrete,
                  target: DoubleArray,
                  @FloatRange(from = 0.0, to = 1.0) splitPoint1: Double,
                  @FloatRange(from = 0.0, to = 1.0) splitPoint2: Double):
        Pair<MultiTimeSeriesDiscrete, DoubleArray>
{
    require(splitPoint1 < splitPoint2)
    require(input.stepCount() == target.size)

    val splitIndex: Int = (input.stepCount() * splitPoint1).toInt()
    val splitIndex2: Int = (input.stepCount() * splitPoint2).toInt()
    val trainRange = splitIndex until splitIndex2

    val trainInput = MultiTimeSeriesDiscrete.fromSubSlice(input, trainRange.first, trainRange.last - 1)
    val trainTarget = target.slice(trainRange).toDoubleArray()

    return Pair(trainInput, trainTarget)
}

fun evaluateModel(input: MultiTimeSeriesDiscrete, target: TimeSeriesDiscretizer.SingleDiscreteTimeSeries): List<DoubleArray> {
    val (trainingInput, trainingTarget) = trainingSplit(input, target.values, 0.0, 0.75)

    DifferentialPredictionModel.train(
        trainingInput,
        trainingTarget
    )

    // retrieve step-wise predictions
    val predictionsDerivative = producePredictions(DifferentialPredictionModel, input)

    // offset initial value
    val startOffset = target.values.slice(0 until 10).average()
    val predictions = predictionsDerivative.discreteTrapezoidalIntegral(startOffset)

    return listOf(predictions, predictionsDerivative, centeredMovingAverage(target.values, window = 64).discreteDerivative().map{
            x -> x.coerceIn(-0.1, 0.1)
    }.toDoubleArray())

}