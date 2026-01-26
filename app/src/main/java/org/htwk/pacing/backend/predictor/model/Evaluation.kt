package org.htwk.pacing.backend.predictor.model

import androidx.annotation.FloatRange
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.model.DifferentialPredictionModel.futureOffset
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
                  @FloatRange(from = 0.0, to = 1.0) splitPoint: Double): Pair<MultiTimeSeriesDiscrete, DoubleArray>
{
    val splitIndex: Int = (input.stepCount() * splitPoint).toInt()
    val trainRange = 0 until splitIndex
    //val trainRange = splitIndex until multiTimeSeriesDiscrete.stepCount() - 1

    val trainInput = MultiTimeSeriesDiscrete.fromSubSlice(input, trainRange.first, trainRange.last - 1)
    val trainTarget = target.slice(trainRange).toDoubleArray()

    return Pair(trainInput, trainTarget)
}

fun evaluateModel(input: MultiTimeSeriesDiscrete, target: TimeSeriesDiscretizer.SingleDiscreteTimeSeries): DoubleArray {
    val (trainingInput, trainingTarget) = trainingSplit(input, target.values, 0.6)

    DifferentialPredictionModel.train(
        trainingInput,
        trainingTarget
    )

    // retrieve step-wise predictions
    val predictionsDerivative = producePredictions(DifferentialPredictionModel, input)

    // offset initial value
    val startOffset = target.values.slice(0 until 10).average()
    val predictionsIntegrated = predictionsDerivative.discreteTrapezoidalIntegral(startOffset)

    val predictionsSmoothed = predictionsIntegrated//centeredMovingAverage(predictionsIntegrated, window = 16)
    val predictions = DoubleArray(predictionsSmoothed.size)
    for(i in 0 until predictionsSmoothed.size - futureOffset) {
        predictions[i + futureOffset] = predictionsSmoothed[i]// + futureOffset]
    }
    /*for(i in 10 until predictions.size) {
        predictions[i] = 0.0//target.slice(i - 1 until i).average()
    }*/

    return predictions
}