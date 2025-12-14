
package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.model.IPredictionModel.PredictionHorizon
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete.FeatureID
import org.htwk.pacing.backend.predictor.preprocessing.PIDComponent
import org.htwk.pacing.backend.predictor.stats.StochasticDistribution
import org.htwk.pacing.backend.predictor.stats.denormalize
import org.htwk.pacing.backend.predictor.stats.normalize
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
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
object LinearCombinationPredictionModel : IPredictionModel {
    //stores "learned" / regressed linear coefficients
    private var linearCoefficients: Map<PredictionHorizon, List<Double>> = mapOf()
    private var stochasticDistributions: Map<FeatureID, StochasticDistribution> = mapOf()
    private var targetStochasticDistribution: StochasticDistribution? = null

    /**
     * Represents a single training sample containing extrapolated multi-signal data
     * and its corresponding expected energy level (target output).
     *
     * @property multiExtrapolations Flattened list of extrapolated feature values from multiple signals.
     * @property expectedEnergyLevel The expected (ground truth) energy level for this sample.
     */
    data class TrainingSample(
        val multiExtrapolations: List<Double>,
        val expectedEnergyLevel: Double
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
                expectedEnergyLevel = targetTimeSeriesDiscrete[offset + predictionLookAhead]
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

        return flatExtrapolationResults
    }


    /**
     * Performs regression training on the currently stored training samples to compute
     * the linear coefficients for the prediction model using Tikhonov regularization.
     *
     * @throws IllegalStateException if no training samples have been added.
     */
    fun trainForHorizon(trainingSamples: List<TrainingSample>): List<Double> {
        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }

        val allExtrapolations = trainingSamples.map { it.multiExtrapolations }
        val allExpectedFutureValues = trainingSamples.map { it.expectedEnergyLevel }

        val allExtrapolationsMatrix: NDArray<Double, D2> = mk.ndarray(allExtrapolations)
        val allExpectedFutureValuesVector: NDArray<Double, D1> = mk.ndarray(allExpectedFutureValues)

        return leastSquaresTikhonov(allExtrapolationsMatrix, allExpectedFutureValuesVector).toList()
    }

    /**
     * Trains the prediction model using the provided training samples.
     *
     * @param input The [MultiTimeSeriesDiscrete] object containing the preprocessed
     *              time series data, such as heart rate.
     * @param targetTimeSeriesDiscrete The target energy level data used for training.
     */
    fun train(input: MultiTimeSeriesDiscrete, targetTimeSeriesDiscrete: DoubleArray) {
        //normalize each feature row and store the normalization parameters per feature
        stochasticDistributions =
            input.getAllFeatureIDs()
                .associateWith { featureID ->
                    input.getMutableRow(featureID).normalize()
                }

        //Also normalize the target variable and store its distribution
        val targetArray = mk.ndarray(targetTimeSeriesDiscrete)
        targetStochasticDistribution = targetArray.normalize()

        linearCoefficients = PredictionHorizon.entries.associateWith { predictionHorizon ->
            val trainingSamples = createTrainingSamples(
                input, targetArray.toDoubleArray(), // Use the now-normalized target data
                predictionHorizon
            )
            trainForHorizon(trainingSamples)
        }
    }

    //TODO: add needs train check, the job should use this to check if we lost weights from restart
    //TODO: update Prediction Job for different Horizons
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
     * @return A [Double] representing the predicted energy level.
     */
    override fun predict(
        input: MultiTimeSeriesDiscrete,
        predictionHorizon: PredictionHorizon
    ): Double {
        require(linearCoefficients.isNotEmpty()) { "No coefficients generated yet, run training first." }
        require(targetStochasticDistribution != null) { "Target distribution not available. Run training." }

        input.getAllFeatureIDs().forEach { featureID ->
            input.getMutableRow(featureID).normalize(stochasticDistributions[featureID]!!)
        }

        val flattenedExtrapolations =
            generateFlattenedMultiExtrapolationResults(input, 0, predictionHorizon)

        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations)
        val coefficientsVector: D1Array<Double> =
            mk.ndarray(linearCoefficients[predictionHorizon]!!)

        val prediction = mk.ndarray(listOf(mk.linalg.dot(extrapolationsVector, coefficientsVector)))
        prediction.denormalize(targetStochasticDistribution!!)


        // Denormalize the prediction to return it to the original scale
        return prediction.first()
    }
}
