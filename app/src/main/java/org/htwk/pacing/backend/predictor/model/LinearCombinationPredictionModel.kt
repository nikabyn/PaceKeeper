package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.preprocessing.MultiTimeSeriesDiscrete
import org.htwk.pacing.backend.predictor.preprocessing.PIDComponent
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.slice
import org.jetbrains.kotlinx.multik.ndarray.operations.first
import org.jetbrains.kotlinx.multik.ndarray.operations.toList
import kotlin.time.Duration.Companion.hours

/**
 * A linear regression–based prediction model that combines multiple extrapolated time series signals
 * (e.g., heart rate, integrals, derivatives) into a single predicted energy level.
 *
 * The model learns linear coefficients via Tikhonov regularized least squares regression
 * using preprocessed time-series data provided by an [IPreprocessor].
 */
object LinearCombinationPredictionModel : IPredictionModel {
    //stores "learned" / regressed linear coefficients per prediction horizon
    private var linearCoefficients: Map<PredictionHorizon, D1Array<Double>> = mapOf()

    enum class PredictionHorizon(val howFarInSamples: Int) {
        NOW((0.hours / Predictor.TIME_SERIES_STEP_DURATION).toInt()),
        FUTURE((2.hours / Predictor.TIME_SERIES_STEP_DURATION).toInt()),
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

            val extrapolations = LinearExtrapolator.multipleExtrapolate(timeSeries, predictionHorizon.howFarInSamples).extrapolations

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

    fun train(input: MultiTimeSeriesDiscrete, target: DoubleArray) {
        require(input.stepCount() == target.size)

        val extrapolationsMatrix: D2Array<Double> = mk.ndarray(
            (0 until input.stepCount()).map { offset ->
            generateFlattenedMultiExtrapolationResults(input, offset)
            }
        )

        linearCoefficients = PredictionHorizon.entries.associateWith { horizon ->

            val targets = target.slice(
                (Predictor.TIME_SERIES_SAMPLE_COUNT - 1) + horizon.howFarInSamples until target.size - horizon.howFarInSamples
            )
            val extrapolations = extrapolationsMatrix.slice<Double, D2, D2>(inSlice=(
                    0 until targets.size
            ), axis = 0)

            leastSquaresTikhonov(extrapolations, mk.ndarray(targets))
        }
    }

    /*fun train(input: MultiTimeSeriesDiscrete, targetTimeSeriesDiscrete: DoubleArray) {
        val trainingSamples = createTrainingSamples(input, targetTimeSeriesDiscrete)

        require(trainingSamples.isNotEmpty()) { "No training samples available, can't perform regression." }
        require(input.stepCount() == targetTimeSeriesDiscrete.size) {
            "Discretized prediction target" +
                    "time series has length ${targetTimeSeriesDiscrete.size} but input" +
                    "MultiTimeSeriesDiscrete has length ${input.stepCount()}"
        }

        val allExtrapolations = trainingSamples.map { it.multiExtrapolations }
        val allExpectedFutureValues = trainingSamples.map { it.expectedEnergyLevel }

        val allExtrapolationsMatrix: NDArray<Double, D2> = mk.ndarray(allExtrapolations)
        val allExpectedFutureValuesVector: NDArray<Double, D1> = mk.ndarray(allExpectedFutureValues)

        linearCoefficients =
            leastSquaresTikhonov(allExtrapolationsMatrix, allExpectedFutureValuesVector).toList()
    }*/

    //TODO: add needs train check, the job should use this to check if we lost weights from restart

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
    override fun predict(input: MultiTimeSeriesDiscrete, predictionHorizon: PredictionHorizon): Double {
        require(linearCoefficients.isNotEmpty()) { "No coefficients generated yet, run training first." }

        val flattenedExtrapolations = generateFlattenedMultiExtrapolationResults(input)

        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations)
        val coefficientsVector: D1Array<Double> = linearCoefficients[predictionHorizon]!!

        val prediction = mk.linalg.dot(extrapolationsVector, coefficientsVector)
        return prediction
    }
}