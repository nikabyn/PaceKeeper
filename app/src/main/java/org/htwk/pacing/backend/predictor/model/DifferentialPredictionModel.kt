
package org.htwk.pacing.backend.predictor.model

//import android.util.Log
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.stats.StochasticDistribution
import org.htwk.pacing.backend.predictor.stats.normalize
import org.htwk.pacing.backend.predictor.stats.normalizeSingleValue
import org.htwk.pacing.ui.math.centeredMovingAverage
import org.htwk.pacing.ui.math.discreteDerivative
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.first
import org.jetbrains.kotlinx.multik.ndarray.operations.toList
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * A linear regression–based prediction model that combines multiple extrapolated time series signals
 * (e.g., heart rate, integrals, derivatives) into a single predicted energy level.
 *
 * The model learns linear coefficients via Tikhonov regularized least squares regression
 * using preprocessed time-series data provided by an [IPreprocessor].
 */
object DifferentialPredictionModel : IPredictionModel {
    private var LOGGING_TAG = "DifferentialPredictionModel"

    //TODO: add sleep score, Anaerobic threshold passed score, ratios of 7-day-
    //
    // baseline vs current for different metrics
    val horizons = listOf(0, 2, 4, 8, 12, 16, 24, 32, 40, 64).map{x -> x}
    val futureOffset = 12 //how far to shift target

    //stores "learned" / regressed linear coefficients per Offset
    class Model(
        //model parameters per prediction horizon (e.g. now vs. future)
        val weights: Map<Int, List<Double>>,
        val extrapolationDistributions: List<StochasticDistribution>

    )

    var model: Model? = null //hold everything in a single state, (model weights etc.)*/

    data class TrainingSample(
        val metricValues: List<Double>,
        val targetValue: Double
    )
    private fun createTrainingSamples(
        input: MultiTimeSeriesDiscrete,
        targetTimeSeriesDiscrete: DoubleArray,
    ): List<TrainingSample> {

        return (0 until input.stepCount() - futureOffset).map { offset ->
            TrainingSample(
                metricValues = input
                    .allFeaturesAt(offset).toList(),
                targetValue = targetTimeSeriesDiscrete[offset + futureOffset]
            )
        }
    }
    override fun train(input: MultiTimeSeriesDiscrete, target: DoubleArray) {
        val softenedTarget = centeredMovingAverage(target, window = 16).discreteDerivative()

        val trainingSamples = createTrainingSamples(
            input,
            softenedTarget
        )

        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }

        val metricMatrix: NDArray<Double, D2> = mk.ndarray(trainingSamples.map { it.metricValues }).transpose()
        val targetVector: NDArray<Double, D1> = mk.ndarray(trainingSamples.map { it.targetValue })

        //normalize extrapolations, this is essential for good regression stability, but skip the
        //constant bias feature at the end so it doesn't get zeroed from normalization
        val extrapolationDistributions = (0 until metricMatrix.shape[0]).map { i ->
            (metricMatrix[i] as D1Array<Double>).normalize()
        }

        val coefficientsMap = mutableMapOf<Int, List<Double>>()

        for(offs in horizons) {
            val coefficients = trainForOffset(metricMatrix, targetVector, offs)
            coefficientsMap[offs] = coefficients
        }

        val ids = input.getAllFeatureIDs().toList()

        model = Model(coefficientsMap, extrapolationDistributions)
    }

    //returns coefficients for offset
    fun trainForOffset(metricMatrix: D2Array<Double>, targetVector: D1Array<Double>, offs: Int) : List<Double>{

        val metricMatrixOffset = (metricMatrix[0 until metricMatrix.shape[0], offs until metricMatrix.shape[1] - offs]) as D2Array<Double>
        //same for target vector
        val targetVectorOffset = (targetVector[offs until targetVector.size - offs]) as D1Array<Double>

        val coefficients = leastSquaresTikhonov(metricMatrixOffset.transpose(), targetVectorOffset).toList()

        return coefficients
    }

    fun predictStepForOffset(input: List<Double>, offs: Int): Double {
        require(model != null) { "No model trained, can't perform prediction." }

        val perHorizonModel = model!!

        //drop last element, because it is the bias, normalizing it is useless anyways
        val flattenedExtrapolations = input

        //normalize extrapolations, this is essential for good regression stability
        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations
            .mapIndexed {index, d ->
            val distribution = perHorizonModel.extrapolationDistributions[index]
            normalizeSingleValue(d, distribution)
        })

        val weights: List<Double> = perHorizonModel.weights[offs]!!

        //get extrapolation weights (how much each extrapolation trend affects the prediction)
        val extrapolationWeights: D1Array<Double> = mk.ndarray(weights)

        val prediction = mk.ndarray(listOf(mk.linalg.dot(extrapolationsVector, extrapolationWeights)))
        return prediction.first()
    }

    override fun predict(
        input: MultiTimeSeriesDiscrete,
        predictionHorizon: PredictionHorizon
    ): Double {
        require(predictionHorizon == PredictionHorizon.NOW)
        val prediction = predictStep(input)
        return prediction
    }

    private fun predictStep(
        inputMTSD: MultiTimeSeriesDiscrete,
    ): Double {
        var sum = 0.0
        var count = 0

        val lastIndex = inputMTSD.stepCount() - 1

        print(lastIndex)
        for(offs in horizons) {
            val allMetricsAtStep = inputMTSD.allFeaturesAt(lastIndex - offs).toList()
            val energyDifference = predictStepForOffset(allMetricsAtStep, offs)
            sum += energyDifference
            count += 1
        }

        if(count == 0) return 0.0

        return sum / count.toDouble()
    }
}
