package org.htwk.pacing.backend.predictor.model

//import android.util.Log
import org.htwk.pacing.backend.predictor.Log
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

    //bias is a constant linear offset to our prediction (e.g. a weight for constant 1.0 "input")
    private const val USE_BIAS = true
    private val BIAS_FEATURE = if (USE_BIAS) listOf<Double>(1.0) else listOf<Double>()

    //used to constrain outliers in input data
    private const val MAX_CHANGE_PER_STEP: Double = 0.05


    //lookback "heads" of our model to help our model respond to events at time offsets
    private val lookBackOffsets = listOf(0, 1, 2, 4, 8, 12, 24, 36, 48, 72, 96)

    private class PerHorizonModel(val weights: List<Double>)

    //store model configuration for each prediction horizon (we train an instance of the model for each horizon)
    //but feature time series distributions stay same
    private class Model(
        val perHorizonModels: Map<PredictionHorizon, PerHorizonModel>,
        val inputDistributions: List<StochasticDistribution>
    )

    //null means that we haven't trained yet
    private var model: Model? = null

    //single example / timestep for the model to train on
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

    /**
     * Prepares the target time series for training by calculating its clamped discrete derivative.
     * This shifts the learning problem from predicting absolute values to predicting the *change* per step,
     * while clamping the change to `[-MAX_CHANGE_PER_STEP, MAX_CHANGE_PER_STEP]` prevents outliers from
     * skewing the model.
     *
     * @param target The raw target time series.
     * @return A new array representing the clamped discrete derivative of the target.
     */
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
        val featureDistributions =
            (0 until metricMatrix.shape[0] - BIAS_FEATURE.size).map { i ->
                (metricMatrix[i] as D1Array<Double>).normalize()
            }

        val perHorizonModels = mutableMapOf<PredictionHorizon, PerHorizonModel>()
        for (predictionHorizon in PredictionHorizon.entries) {
            val coefficients = trainForHorizon(metricMatrix, targetVector, predictionHorizon)
            perHorizonModels[predictionHorizon] = PerHorizonModel(coefficients)

            Log.i(LOGGING_TAG, "Trained model for horizon $predictionHorizon")
            Log.i(LOGGING_TAG, "Weights: ${coefficients.joinToString(", ")}")
            Log.i(LOGGING_TAG, "Distributions: ${featureDistributions.joinToString(", ")}")
        }

        model = Model(perHorizonModels, featureDistributions)
    }

    /**
     * Solves the linear system for a specific prediction horizon.
     * Shifts the input and target arrays so that input[t] tries to predict target[t + horizon].
     *
     * @param metricMatrix The matrix of input features.
     * @param targetVector The target values.
     * @param predictionHorizon The prediction horizon.
     */
    private fun trainForHorizon(
        metricMatrix: D2Array<Double>,
        targetVector: D1Array<Double>,
        predictionHorizon: PredictionHorizon
    ): List<Double> {
        require(metricMatrix.shape[1] == targetVector.size) {
            "metricMatrix and targetVector must have the same length ${metricMatrix.shape[1]} != ${targetVector.size}"
        }

        //create a shift that will lead our model to predict future energy changes where appropriate
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

        //determine "optimal" weights for energy prediction
        //solve the overdetermined linear equation system of:
        //(feature matrix * weights) = (energy changes)
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
        require(model != null) { "No model trained, can't perform prediction." }

        //return 0 change for edge case at edge of input time series
        if (offset < lookBackOffsets.max()) return 0.0

        require(offset in 0 until input.stepCount())

        val inputDistributions = model!!.inputDistributions
        val perHorizonModel = model!!.perHorizonModels[horizon]!!

        val inputFeaturesAtOffset = createFeatures(input, offset)

        //denormalize features, using distributions generated on training
        val denormalizedInputs: D1Array<Double> = mk.ndarray(
            mk.ndarray(
                inputFeaturesAtOffset
                    .mapIndexed { index, d ->
                        val distribution = inputDistributions[index]
                        normalizeSingleValue(d, distribution)
                    }).toList() + BIAS_FEATURE //add bias after normalization
        )

        val weights: List<Double> = perHorizonModel.weights

        //get extrapolation weights (how much each extrapolation trend affects the prediction)
        val extrapolationWeights: D1Array<Double> = mk.ndarray(weights)

        //result is the dot product: w0*x0 + w1*x1 + ... + bias
        //since we trained on discrete derivatives, this output is a "delta" (energy change)
        val prediction = mk.linalg.dot(denormalizedInputs, extrapolationWeights)
        return prediction
    }
}
