package org.htwk.pacing.backend.predictor.model

//import android.util.Log
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
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

/**
 * A linear regression–based prediction model that combines multiple extrapolated time series signals
 * (e.g., heart rate, integrals, derivatives) into a single predicted energy level.
 *
 * The model learns linear coefficients via Tikhonov regularized least squares regression
 * using preprocessed time-series data provided by an [IPreprocessor].
 */
object DifferentialPredictionModel : IPredictionModel {
    private var LOGGING_TAG = "DifferentialPredictionModel"

    private const val USE_BIAS = true
    private const val MAX_CHANGE_PER_STEP: Double = 0.05

    //TODO: add sleep score, Anaerobic threshold passed score, ratios of 7-day-
    //baseline vs current for different metrics
    private val BIAS_FEATURE = if (USE_BIAS) listOf<Double>(1.0) else listOf<Double>()
    val lookBackOffsets = listOf(0, 1, 2, 4, 8, 12, 24, 36, 48)

    class PerHorizonModel(val weights: List<Double>)
    class Model(
        val perHorizonModels: Map<PredictionHorizon, PerHorizonModel>,
        val inputDistributions: List<StochasticDistribution>
    )

    var model: Model? = null

    data class TrainingSample(
        val metricValues: List<Double>,
        val targetValue: Double
    )

    private fun createFeatures(input: MultiTimeSeriesDiscrete, offset: Int): List<Double> {
        return lookBackOffsets.map { horizon ->
            val index = (offset - horizon)
            input.allFeaturesAt(index).toList()
        }.flatten()
    }

    private fun createTrainingSamples(
        input: MultiTimeSeriesDiscrete,
        targetTimeSeriesDiscrete: DoubleArray
    ): List<TrainingSample> {

        return (lookBackOffsets.max() until input.stepCount()).map { offset ->
            TrainingSample(
                metricValues = createFeatures(input, offset) + BIAS_FEATURE,
                targetValue = targetTimeSeriesDiscrete[offset]
            )
        }
    }

    fun prepareTargetFeature(target: DoubleArray): DoubleArray {
        return target.discreteDerivative().map { x ->
            x.coerceIn(-MAX_CHANGE_PER_STEP, MAX_CHANGE_PER_STEP)
        }.toDoubleArray()
    }

    override fun train(input: MultiTimeSeriesDiscrete, target: DoubleArray) {
        val softenedTarget = prepareTargetFeature(target)

        val trainingSamples = createTrainingSamples(
            input,
            softenedTarget
        )

        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }

        val metricMatrix: NDArray<Double, D2> =
            mk.ndarray(trainingSamples.map { it.metricValues }).transpose()
        val targetVector: NDArray<Double, D1> = mk.ndarray(trainingSamples.map { it.targetValue })

        //normalize extrapolations, this is essential for good regression stability, but skip the
        //constant bias feature at the end so it doesn't get zeroed from normalization
        val extrapolationDistributions =
            (0 until metricMatrix.shape[0] - BIAS_FEATURE.size).map { i ->
                (metricMatrix[i] as D1Array<Double>).normalize()
            }

        val perHorizonModels = mutableMapOf<PredictionHorizon, PerHorizonModel>()
        for (predictionHorizon in PredictionHorizon.entries) {
            val coefficients = trainForHorizon(metricMatrix, targetVector, predictionHorizon)
            perHorizonModels[predictionHorizon] = PerHorizonModel(coefficients)
        }

        model = Model(perHorizonModels, extrapolationDistributions)
    }

    //returns coefficients for offset
    fun trainForHorizon(
        metricMatrix: D2Array<Double>,
        targetVector: D1Array<Double>,
        predictionHorizon: PredictionHorizon
    ): List<Double> {
        require(metricMatrix.shape[1] == targetVector.size) {
            "metricMatrix and targetVector must have the same length ${metricMatrix.shape[1]} != ${targetVector.size}"
        }

        val metricMatrixShifted = (metricMatrix[
            0 until metricMatrix.shape[0],
            0 until metricMatrix.shape[1] - predictionHorizon.howFarInSamples
        ]) as D2Array<Double>
        //same for target vector
        val targetVectorShifted = (targetVector[
            predictionHorizon.howFarInSamples until targetVector.size
        ]) as D1Array<Double>

        require(metricMatrixShifted.shape[1] == targetVectorShifted.size) {
            "metricMatrix and targetVector must have the same length ${metricMatrixShifted.shape[0]} != ${targetVectorShifted.size}"
        }

        val coefficients = leastSquaresTikhonov(
            metricMatrixShifted.transpose(), targetVectorShifted,
            regularization = 1e-6,
            lastIsBias = USE_BIAS
        ).toList()

        return coefficients
    }

    override fun predict(
        input: MultiTimeSeriesDiscrete,
        offset: Int,
        horizon: PredictionHorizon,
    ): Double {
        if (offset < lookBackOffsets.max()) return 0.0 //QUICKFIX

        require(offset in 0 until input.stepCount())
        require(model != null) { "No model trained, can't perform prediction." }

        val inputDistributions = model!!.inputDistributions
        val perHorizonModel = model!!.perHorizonModels[horizon]!!

        val inputFeaturesAtOffset = createFeatures(input, offset)

        //normalize extrapolations, this is essential for good regression stability
        val normalizedInputs: D1Array<Double> = mk.ndarray(
            mk.ndarray(
            inputFeaturesAtOffset
                .mapIndexed { index, d ->
                    val distribution = inputDistributions[index]
                    normalizeSingleValue(d, distribution)
                }).toList() + BIAS_FEATURE
        )

        val weights: List<Double> = perHorizonModel.weights

        //get extrapolation weights (how much each extrapolation trend affects the prediction)
        val extrapolationWeights: D1Array<Double> = mk.ndarray(weights)

        //TODO: remove this and maybe do normalize target
        val prediction =
            mk.ndarray(listOf(mk.linalg.dot(normalizedInputs, extrapolationWeights))).first()
        return prediction//.coerceIn(-0.01, 0.01)
    }
}
