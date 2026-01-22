
package org.htwk.pacing.backend.predictor.model

//import android.util.Log
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesDiscretizer
import org.htwk.pacing.backend.predictor.stats.StochasticDistribution
import org.htwk.pacing.backend.predictor.stats.normalize
import org.htwk.pacing.backend.predictor.stats.normalizeSingleValue
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
import kotlin.toString

/**
 * A linear regression–based prediction model that combines multiple extrapolated time series signals
 * (e.g., heart rate, integrals, derivatives) into a single predicted energy level.
 *
 * The model learns linear coefficients via Tikhonov regularized least squares regression
 * using preprocessed time-series data provided by an [IPreprocessor].
 */
object DifferentialPredictionModel : IPredictionModel {
    private var LOGGING_TAG = "DifferentialPredictionModel"

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

    fun train(input: MultiTimeSeriesDiscrete, targetTimeSeriesDiscrete: DoubleArray) {
        val energyChanges = targetTimeSeriesDiscrete.discreteDerivative()

        val trainingSamples = createTrainingSamples(
            input,
            energyChanges
        )

        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }

        val metricMatrix: NDArray<Double, D2> = mk.ndarray(trainingSamples.map { it.metricValues }).transpose()
        val targetVector: NDArray<Double, D1> = mk.ndarray(trainingSamples.map { it.targetValue })

        //normalize extrapolations, this is essential for good regression stability, but skip the
        //constant bias feature at the end so it doesn't get zeroed from normalization
        val extrapolationDistributions = (0 until metricMatrix.shape[0] - 1).map { i ->
            (metricMatrix[i] as D1Array<Double>).normalize()
        }

        val coefficientsMap = mutableMapOf<Int, List<Double>>()

        for(offs in 5 until 10) {
            val coefficients = trainForOffset(metricMatrix, targetVector, offs)
            coefficientsMap[offs] = coefficients
        }

        val ids = input.getAllFeatureIDs().toList()

        /*println()
        coefficients.dropLast(1).forEachIndexed { i, x ->
            print("${ids[i].metric}, ${ids[i].component.toString().substring(0,4)}; ")
            println("%.6f".format(x * 1000.0))
        }
        print("bias:")
        println("%.6f".format(coefficients.last() * 1000.0))
        println()*/

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

        val perHorizonModel = model!!//.perHorizonModels[predictionHorizon]!!

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

        val weights: List<Double> = perHorizonModel.weights[offs]!!

        //get extrapolation weights (how much each extrapolation trend affects the prediction)
        val extrapolationWeights: D1Array<Double> = mk.ndarray(weights)

        val prediction = mk.ndarray(listOf(mk.linalg.dot(extrapolationsVector, extrapolationWeights)))
        //denormalize prediction out of normalized spaces

        //Log.i(LOGGING_TAG, "prediction result: ${prediction.first()}")

        return prediction.first()
    }

    /*override fun predict(
        input: MultiTimeSeriesDiscrete,
        predictionHorizon: PredictionHorizon,
    ): Double {
        var currentEnergy = 0.0
        for(i in 0 until input.stepCount()) {
            val currentMetrics = input.allFeaturesAt(i).toList()
            currentEnergy += predictStep(currentMetrics)
        }
        return currentEnergy
    }*/

    override fun backTestMany(
        inputMTSD: MultiTimeSeriesDiscrete,
        targetTimeSeries: TimeSeriesDiscretizer.SingleDiscreteTimeSeries,
        predictionHorizon: PredictionHorizon
    ): DoubleArray {
        val predictions = DoubleArray(inputMTSD.stepCount()) {0.0}

        val entries = targetTimeSeries.buckets.entries.toList()
        val maxK = 2
        //val maxK = entries.size - 2
        for(k in 0 until maxK) {
            val entry = entries[k]
            val nextEntry = entries[k + 1]

            val nextIndex = predictions.size
            //val nextIndex = nextEntry.key

            val lastEnteredEnergy = entry.value
            var currentEnergy = lastEnteredEnergy
            for(i in entry.key until nextIndex)
            {
                //for(i in entry.first until nextEntry.first){//multiTimeSeriesDiscrete.stepCount()) {
                val energyDifference = predictStep(inputMTSD, i)
                currentEnergy += energyDifference
                predictions[i] = currentEnergy
            }
        }

        return predictions
    }

    private fun predictStep(
        inputMTSD: MultiTimeSeriesDiscrete,
        step: Int
    ): Double {
        var sum = 0.0
        var count = 0
        for(offs in 5 until 10) {
            if(step - offs < 0 || step + offs >= inputMTSD.stepCount()) continue
            val allMetricsAtStep = inputMTSD.allFeaturesAt(step - offs).toList()
            val energyDifference = predictStepForOffset(allMetricsAtStep, offs)
            sum += energyDifference
            count += 1
        }

        return sum / count.toDouble()
    }
}
