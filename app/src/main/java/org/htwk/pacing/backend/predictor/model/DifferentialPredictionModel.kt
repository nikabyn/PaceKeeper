package org.htwk.pacing.backend.predictor.model

//import android.util.Log
import org.htwk.pacing.backend.predictor.discreteDerivative
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.stats.StochasticDistribution
import org.htwk.pacing.backend.predictor.stats.normalize
import org.htwk.pacing.backend.predictor.stats.normalizeSingleValue
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
import org.jetbrains.kotlinx.multik.ndarray.operations.toList


/**
 * A linear regression–based prediction model that operates on the *discrete derivative* of the target signal.
 * This model aims to predict the *change* in the target value rather than the absolute value itself.
 *
 * ### How it works:
 * 1.  **Feature Creation**: For each time step, it creates a feature vector by looking back at past values of the input time series. The `lookBackOffsets` define how far back in time to look (e.g., current value, 1 step ago, 2 steps ago, etc.). These features from all input series are flattened into a single vector.
 * 2.  **Target Preparation**: Instead of using the raw target values, it calculates the discrete derivative of the target signal. This derivative is also clamped to a `MAX_CHANGE_PER_STEP` to prevent extreme values from dominating the training process.
 * 3.  **Training**:
 *     - It trains a separate set of linear regression weights for each `PredictionHorizon` (e.g., NOW, FUTURE).
 *     - The training uses Tikhonov-regularized least squares to find the optimal weights that map the feature vectors to the prepared target (the discrete derivative).
 *     - Input features are normalized before training to improve numerical stability. A bias term can optionally be included.
 * 4.  **Prediction**:
 *     - To make a prediction for a given time step and horizon, it first constructs the feature vector for that step.
 *     - The features are normalized using the distributions calculated during training.
 *     - The model then performs a dot product between the normalized feature vector and the learned weights corresponding to the specified prediction horizon.
 *     - The result is the predicted *change* in the target value for the next step.
 *
 * This differential approach can make the model more robust to drifts and shifts in the absolute level of the target signal, as it focuses on learning the dynamics of its change.
 *
 */
object DifferentialPredictionModel : IPredictionModel {
    private var LOGGING_TAG = "DifferentialPredictionModel"

    private const val USE_BIAS = true
    private const val MAX_CHANGE_PER_STEP: Double = 0.05

    private val BIAS_FEATURE = if (USE_BIAS) listOf<Double>(1.0) else listOf<Double>()
    private val lookBackOffsets = listOf(0, 1, 2, 4, 8, 12, 24, 36, 48, 72, 96)

    private class PerHorizonModel(val weights: List<Double>)
    private class Model(
        val perHorizonModels: Map<PredictionHorizon, PerHorizonModel>,
        val inputDistributions: List<StochasticDistribution>
    )

    private var model: Model? = null

    private data class TrainingSample(
        val metricValues: List<Double>,
        val targetValue: Double
    )

    /**
     * Creates a feature vector for a given time step by sampling historical data.
     *
     * For the given `offset`, this function looks back into the `input` time series at intervals
     * defined by `lookBackOffsets`. It collects all features at each of these past time steps
     * and flattens them into a single list.
     *
     * @param input The multi-variate time series data to sample from.
     * @param offset The current time step index from which to look back.
     * @return A flattened list of historical feature values.
     */
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

    private fun prepareTargetFeature(target: DoubleArray): DoubleArray {
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
    private fun trainForHorizon(
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
        if (offset < lookBackOffsets.max()) return 0.0

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

        val prediction = mk.linalg.dot(normalizedInputs, extrapolationWeights)
        return prediction
    }
}
