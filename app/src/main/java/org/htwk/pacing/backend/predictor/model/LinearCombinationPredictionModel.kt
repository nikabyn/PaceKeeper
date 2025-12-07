package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.PIDComponent
import org.htwk.pacing.backend.predictor.preprocessing.TimeSeriesMetric
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.data.get
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
    private var linearCoefficients: List<Double> = listOf()
    private val predictionTargetFeatureID = MultiTimeSeriesDiscrete.FeatureID(
        TimeSeriesMetric.HEART_RATE,
        PIDComponent.PROPORTIONAL
    )

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

    private fun createTrainingSamples(
        input: MultiTimeSeriesDiscrete,
    ): List<TrainingSample> {
        val targetTimeSeries = input.getMutableRow(predictionTargetFeatureID).toDoubleArray()

        val predictionLookAhead =
            (Predictor.TIME_SERIES_SAMPLE_COUNT - 1) + (Predictor.PREDICTION_WINDOW_SAMPLE_COUNT);

        return (0 until targetTimeSeries.size - predictionLookAhead).map { offset ->
            TrainingSample(
                multiExtrapolations = generateFlattenedMultiExtrapolationResults(input, offset),
                expectedEnergyLevel = targetTimeSeries[offset + predictionLookAhead]
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
        indexOffset: Int = 0
    ): List<Double> {
        val flatExtrapolationResults = input.getAllFeatureIDs().flatMap { featureID ->
            val timeSeries = input.getMutableRow((featureID))

            val extrapolations = LinearExtrapolator.multipleExtrapolate(
                timeSeries,
                indexOffset
            ).extrapolations

            extrapolations.map { (_, line) ->
                val result = line.getExtrapolationResult()
                if (featureID.component == PIDComponent.INTEGRAL) result - timeSeries[indexOffset] else result
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
    fun train(input: MultiTimeSeriesDiscrete) {
        val trainingSamples = createTrainingSamples(input)

        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }

        val allExtrapolations = trainingSamples.map { it.multiExtrapolations }
        val allExpectedFutureValues = trainingSamples.map { it.expectedEnergyLevel }

        val allExtrapolationsMatrix: NDArray<Double, D2> = mk.ndarray(allExtrapolations)
        val allExpectedFutureValuesVector: NDArray<Double, D1> = mk.ndarray(allExpectedFutureValues)

        linearCoefficients =
            leastSquaresTikhonov(allExtrapolationsMatrix, allExpectedFutureValuesVector).toList()
    }

    /**
     * Predicts the next energy level data point based on the preprocessed time series data.
     * This model uses a linear combination of various time series features (e.g., integrals, derivatives)
     * that have been extrapolated into the future.
     *
     * The prediction is calculated as the dot product / linear combination of the flattened extrapolation results vector
     * and the "learned"(regressed) linear coefficients vector. The learned coefficients vector
     *
     * @param input The [IPreprocessor.MultiTimeSeriesDiscrete] object containing the preprocessed
     *              time series data, such as heart rate.
     * @return A [Double] representing the predicted energy level.
     */
    override fun predict(input: MultiTimeSeriesDiscrete): Double {
        val flattenedExtrapolations = generateFlattenedMultiExtrapolationResults(input)

        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations)
        val coefficientsVector: D1Array<Double> = mk.ndarray(linearCoefficients)

        val prediction = mk.linalg.dot(extrapolationsVector, coefficientsVector)
        return prediction

    }
}