
package org.htwk.pacing.backend.predictor.model

//import android.util.Log
import org.htwk.pacing.backend.predictor.Log
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.PIDComponent
import org.htwk.pacing.backend.predictor.stats.StochasticDistribution
import org.htwk.pacing.backend.predictor.stats.denormalize
import org.htwk.pacing.backend.predictor.stats.normalize
import org.htwk.pacing.backend.predictor.stats.normalizeSingleValue
import org.htwk.pacing.ui.math.discreteDerivative
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.data.slice
import org.jetbrains.kotlinx.multik.ndarray.operations.first
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toList
import kotlin.math.absoluteValue

/**
 * A linear regression–based prediction model that combines multiple extrapolated time series signals
 * (e.g., heart rate, integrals, derivatives) into a single predicted energy level.
 *
 * The model learns linear coefficients via Tikhonov regularized least squares regression
 * using preprocessed time-series data provided by an [IPreprocessor].
 */
object DifferentialPredictionModel/* : IPredictionModel*/ {
    private var LOGGING_TAG = "DifferentialPredictionModel"

    //stores "learned" / regressed linear coefficients per Horizon
    data class PerHorizonModel(
        //bias (linear offset so that our fit "line" doesn't have to go through 0)
        //val bias: Double,

        //weight per extrapolation (how much each extrapolated trend affects prediction output)
        val weights: List<Double>,

        //stochastic distribution per extrapolation, we need this for normalization
        val extrapolationDistributions: List<StochasticDistribution>
    )

    class Model(
        //model parameters per prediction horizon (e.g. now vs. future)
        val perHorizonModel: PerHorizonModel,
        //val perHorizonModels: Map<PredictionHorizon, PerHorizonModel>,
    )

    var model: Model? = null //hold everything in a single state, (model weights etc.)*/

    data class TrainingSample(
        val metricValues: List<Double>,
        val targetValue: Double
    )

    var timeOffset = 0

    private fun createTrainingSamples(
        input: MultiTimeSeriesDiscrete,
        targetTimeSeriesDiscrete: DoubleArray,
    ): List<TrainingSample> {

        return (timeOffset until input.stepCount()).map { offset ->
            TrainingSample(
                metricValues = input.allFeaturesAt(offset - timeOffset).toList() + listOf(1.0),
                targetValue = targetTimeSeriesDiscrete[offset]
            )
        }
    }

    /*private fun trainForHorizon(trainingSamples: List<TrainingSample>): PerHorizonModel {

    }*/

    fun train(input: MultiTimeSeriesDiscrete, targetTimeSeriesDiscrete: DoubleArray) {
        val energyChanges = targetTimeSeriesDiscrete.discreteDerivative()

        val trainingSamples = createTrainingSamples(
            input,
            energyChanges
        )

        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }

        val allExtrapolations = trainingSamples.map { it.metricValues }
        val allExpectedFutureValues = trainingSamples.map { it.targetValue }

        val allExtrapolationsMatrix: NDArray<Double, D2> = mk.ndarray(allExtrapolations).transpose()
        val allExpectedFutureValuesVector: NDArray<Double, D1> = mk.ndarray(allExpectedFutureValues)

        //normalize extrapolations, this is essential for good regression stability, but skip the
        //constant bias feature at the end so it doesn't get zeroed from normalization
        val extrapolationDistributions = (0 until allExtrapolationsMatrix.shape[0] - 1).map { i ->
            (allExtrapolationsMatrix[i] as D1Array<Double>).normalize()
        }

        val coefficients = leastSquaresTikhonov(allExtrapolationsMatrix.transpose(), allExpectedFutureValuesVector).toList()

        model = Model (
            PerHorizonModel(coefficients, extrapolationDistributions)
        )

        val ids = input.getAllFeatureIDs().toList()

        println()
        coefficients.dropLast(1).forEachIndexed { i, x ->
            print("${ids[i].metric}, ${ids[i].component.toString().substring(0,4)}; ")
            println("%.6f".format(x * 1000.0))
        }
        print("bias:")
        println("%.6f".format(coefficients.last() * 1000.0))
        println()

    }

    fun predictStep(input: List<Double>): Double {
        require(model != null) { "No model trained, can't perform prediction." }

        val perHorizonModel = model!!.perHorizonModel//.perHorizonModels[predictionHorizon]!!

        //drop last element, because it is the bias, normalizing it is useless anyways
        val flattenedExtrapolations = input//.dropLast(1)
            //generateFlattenedMultiExtrapolationResults(input, 0, predictionHorizon).dropLast(1)

        //Log.i(LOGGING_TAG, "prediction extrapolations: " + flattenedExtrapolations.joinToString(", ") { "%.2e".format(it) })

        //normalize extrapolations, this is essential for good regression stability
        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations
            .mapIndexed {index, d ->
            val distribution = perHorizonModel.extrapolationDistributions[index]
            normalizeSingleValue(d, distribution)
        } + listOf(1.0))

        //get extrapolation weights (how much each extrapolation trend affects the prediction)
        val extrapolationWeights: D1Array<Double> = mk.ndarray(perHorizonModel.weights)

        val prediction = mk.ndarray(listOf(mk.linalg.dot(extrapolationsVector, extrapolationWeights)))
        //denormalize prediction out of normalized spaces

        //Log.i(LOGGING_TAG, "prediction result: ${prediction.first()}")

        return prediction.first()
    }

    fun predict(
        input: MultiTimeSeriesDiscrete,
        predictionHorizon: PredictionHorizon,
    ): Double {
        var currentEnergy = 0.0
        for(i in 0 until input.stepCount()) {
            val currentMetrics = input.allFeaturesAt(i).toList()
            currentEnergy += predictStep(currentMetrics)
        }
        return currentEnergy
    }
}
