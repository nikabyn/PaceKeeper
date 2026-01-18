
package org.htwk.pacing.backend.predictor.model

//import android.util.Log
import org.htwk.pacing.backend.predictor.Log
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.PIDComponent
import org.htwk.pacing.backend.predictor.stats.StochasticDistribution
import org.htwk.pacing.backend.predictor.stats.normalize
import org.htwk.pacing.backend.predictor.stats.normalizeSingleValue
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

/**
 * A linear regression–based prediction model that combines multiple extrapolated time series signals
 * (e.g., heart rate, integrals, derivatives) into a single predicted energy level.
 *
 * The model learns linear coefficients via Tikhonov regularized least squares regression
 * using preprocessed time-series data provided by an [IPreprocessor].
 */
object ExtrapolationPredictionModel : IPredictionModel {
    private var LOGGING_TAG = "ExtrapolationPredictionModel"

    //stores "learned" / regressed linear coefficients per Horizon
    private data class PerHorizonModel(
        //bias (linear offset so that our fit "line" doesn't have to go through 0)
        val bias: Double,

        //weight per extrapolation (how much each extrapolated trend affects prediction output)
        val extrapolationWeights: List<Double>,

        //stochastic distribution per extrapolation, we need this for normalization
        val extrapolationDistributions: List<StochasticDistribution>      )

    private class Model(
        //model parameters per prediction horizon (e.g. now vs. future)
        val perHorizonModels: Map<PredictionHorizon, PerHorizonModel>,

        //stochastic distribution parameters for prediction target (energy), because we need to normalize it too
        val targetStochasticDistribution: StochasticDistribution
    )

    private var model: Model? = null //hold everything in a single state, (model weights etc.)

    /**
     * Represents a single training sample containing extrapolated multi-signal data
     * and its corresponding expected energy level (target output).
     *
     * @property multiExtrapolations Flattened list of extrapolated feature values from multiple signals.
     * @property targetValue The expected (ground truth) value for this sample. We are training to predict this.
     */
    data class TrainingSample(
        val multiExtrapolations: List<Double>,
        val targetValue: Double
    )

    /**
     * Creates a list of training samples from the input time series data.
     *
     * Each sample consists of a set of extrapolated features and the corresponding
     * "ground truth" future value (the expected future value of the  [predictionTargetFeatureID]).
     * This function iterates through the input [MultiTimeSeriesDiscrete] with a sliding window to
     * generate these samples.
     *
     * @param input The multi-time-series data used to generate training samples.
     * @return A list of [TrainingSample] objects, each containing the extrapolated features
     *         and the expected future energy level.
     */
    private fun createTrainingSamples(
        input: MultiTimeSeriesDiscrete,
        targetTimeSeriesDiscrete: DoubleArray,
        predictionHorizon: PredictionHorizon
    ): List<TrainingSample> {
        val predictionLookAhead =
            (Predictor.TIME_SERIES_SAMPLE_COUNT - 1) + (predictionHorizon.howFarInSamples);

        return (0 until input.stepCount() - predictionLookAhead).map { offset ->
            TrainingSample(
                multiExtrapolations = generateFlattenedMultiExtrapolationResults(
                    input,
                    offset,
                    predictionHorizon
                ),
                targetValue = targetTimeSeriesDiscrete[offset + predictionLookAhead]
            )
        }
    }

    /**
     * Generates a flattened list of extrapolation results across all input time series.
     * This includes proportional, integral, and derivative components depending on the signal type.
     *
     * @param input The multi-time-series data to extrapolate.
     * @param indexOffset The index offset into the time series for which extrapolation is performed.
     * @return A flattened list of extrapolated feature values.
     */
    private fun generateFlattenedMultiExtrapolationResults(
        input: MultiTimeSeriesDiscrete,
        indexOffset: Int = 0,
        predictionHorizon: PredictionHorizon
    ): List<Double> {
        val flatExtrapolationResults = input.getAllFeatureIDs().flatMap { featureID ->
            val timeSeries: D1Array<Double> =
                input.getMutableRow(featureID)
                    .slice(indexOffset until indexOffset + Predictor.TIME_SERIES_SAMPLE_COUNT)

            val extrapolations = LinearExtrapolator.multipleExtrapolate(
                timeSeries,
                predictionHorizon.howFarInSamples
            ).extrapolations

            extrapolations.map { (_, line) ->
                val result = line.getExtrapolationResult()
                if (featureID.component == PIDComponent.INTEGRAL) result - timeSeries.first() else result
            }
        }

        //add one constant value to encode bias "weight" (constant offset)
        return flatExtrapolationResults + listOf(1.0)
    }

    /**
     * Performs regression training on the currently stored training samples to compute
     * the linear coefficients for the prediction model using Tikhonov regularization.
     *
     * @throws IllegalStateException if no training samples have been added.
     */
    private fun trainForHorizon(trainingSamples: List<TrainingSample>): PerHorizonModel {
        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }

        val allExtrapolations = trainingSamples.map { it.multiExtrapolations }
        val allExpectedFutureValues = trainingSamples.map { it.targetValue }

        val allExtrapolationsMatrix: NDArray<Double, D2> = mk.ndarray(allExtrapolations).transpose()
        val allExpectedFutureValuesVector: NDArray<Double, D1> = mk.ndarray(allExpectedFutureValues)

        //normalize extrapolations, this is essential for good regression stability, but skip the
        //constant bias feature at the end so it doesn't get zeroed from normalization
        val extrapolationDistributions = (0 until allExtrapolationsMatrix.shape[0] - 1).map { i ->
            (allExtrapolationsMatrix[i] as D1Array<Double>).normalize()
        }

        val coefficients = leastSquaresTikhonov(allExtrapolationsMatrix.transpose(), allExpectedFutureValuesVector).toList()

        val bias = coefficients.last()

        return PerHorizonModel(bias, coefficients.dropLast(1), extrapolationDistributions)
    }

    /**
     * Trains the prediction model using the provided training data.
     *
     * @param input The [MultiTimeSeriesDiscrete] object containing the preprocessed
     *              time series data, such as heart rate.
     * @param targetTimeSeriesDiscrete The target energy level data used for training.
     */
    fun train(input: MultiTimeSeriesDiscrete, targetTimeSeriesDiscrete: DoubleArray) {
        Log.i(LOGGING_TAG, "input duration: ${input.getDuration()}")
        //Also normalize the target variable and store its distribution
        val targetArray = mk.ndarray(targetTimeSeriesDiscrete)
        val targetStochasticDistribution = targetArray.normalize()

        Log.i(LOGGING_TAG, "target data distribution: (mean:" +
                "${targetStochasticDistribution.mean}, " +
                "stddev: ${targetStochasticDistribution.stddev})")

        val perHorizonModels = PredictionHorizon.entries.associateWith { predictionHorizon ->
            val trainingSamples = createTrainingSamples(
                input,
                targetArray.toDoubleArray(),
                predictionHorizon
            )
            Log.i(LOGGING_TAG, "created Training Samples for: ${predictionHorizon.name}")

            val horizonModel = trainForHorizon(trainingSamples)

            Log.i(LOGGING_TAG, "trained model config for ${predictionHorizon.name}: " +
                    "bias: ${horizonModel.bias}")

            Log.i(LOGGING_TAG, "trained model config for ${predictionHorizon.name}: " +
                    "weights: ${horizonModel.extrapolationWeights.joinToString(", ") { "%.2e".format(it) }}")

            horizonModel
        }

        this.model = Model(perHorizonModels, targetStochasticDistribution)
        Log.i(LOGGING_TAG, "model set, training done")
    }

    /**
     * Predicts the next energy level data point based on the preprocessed time series data.
     * This model uses a linear combination of various time series features (e.g., integrals, derivatives)
     * that have been extrapolated into the future.
     *
     * The prediction is calculated as the dot product / linear combination of the flattened extrapolation results vector
     * and the "learned"(regressed) linear coefficients vector. The learned coefficients vector
     *
     * @param input The [MultiTimeSeriesDiscrete] object containing the preprocessed
     *              time series data, such as heart rate.
     * @param predictionHorizon The prediction horizon for which to make the prediction.
     * @return A [Double] representing the predicted energy level.
     */
    override fun predict(
        input: MultiTimeSeriesDiscrete,
        predictionHorizon: PredictionHorizon
    ): Double {
        require(model != null) { "No model trained, can't perform prediction." }

        val perHorizonModel = model!!.perHorizonModels[predictionHorizon]!!

        //drop last element, because it is the bias, normalizing it is useless anyways
        val flattenedExtrapolations =
            generateFlattenedMultiExtrapolationResults(input, 0, predictionHorizon).dropLast(1)

        Log.i(LOGGING_TAG, "prediction extrapolations: " + flattenedExtrapolations.joinToString(", ") { "%.2e".format(it) })

        //normalize extrapolations, this is essential for good regression stability
        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations.mapIndexed {index, d ->
            val distribution = perHorizonModel.extrapolationDistributions[index]
            normalizeSingleValue(d, distribution)
        })

        //get extrapolation weights (how much each extrapolation trend affects the prediction)
        val extrapolationWeights: D1Array<Double> = mk.ndarray(perHorizonModel.extrapolationWeights)

        val prediction = mk.ndarray(listOf(mk.linalg.dot(extrapolationsVector, extrapolationWeights) + perHorizonModel.bias))
        //denormalize prediction out of normalized spaces
        //prediction.denormalize(model!!.targetStochasticDistribution)

        Log.i(LOGGING_TAG, "prediction result: ${prediction.first()}")

        return prediction.first()
    }
}
