package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
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
import org.jetbrains.kotlinx.multik.ndarray.operations.toDoubleArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toList
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

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

    /**
     * Represents a single training sample containing extrapolated multi-signal data
     * and its corresponding expected energy level (target output).
     *
     * @property multiExtrapolations Flattened list of extrapolated feature values from multiple signals.
     * @property expectedEnergyLevel The expected (ground truth) energy level for this sample.
     */
    data class TrainingSample(
        //TODO: (see ui#62) map to actual metric enum -> rework representation of all time series
        // as abstraction with views into a multik matrix
        val multiExtrapolations: List<Double>,
        val expectedEnergyLevel: Double
    )

    private val trainingSamples: MutableList<TrainingSample> = mutableListOf()

    /**
     * Sets the number of samples to skip between training samples when generating training data.
     *
     * @param stepSize The number of time steps between training samples.
     * @throws IllegalArgumentException if [stepSize] is outside the valid range.
     */
    fun setTrainingStepSize(stepSize: Int) {
        require(stepSize in 1..<Predictor.TIME_SERIES_SAMPLE_COUNT) { "Invalid training step size: $stepSize" }
        trainingTimeStepSize = stepSize
    }

    private var DEFAULT_TRAINING_STEPSIZE: Int =
        ((17.hours + 10.minutes) / Predictor.TIME_SERIES_STEP_DURATION).toInt()
    private var trainingTimeStepSize: Int = DEFAULT_TRAINING_STEPSIZE

    fun addTrainingSamplesFromMultiTimeSeriesDiscrete(
        inputMTSD: MultiTimeSeriesDiscrete,
    ) {
        val trainingWindowSize =
            inputMTSD.length() - Predictor.TIME_SERIES_SAMPLE_COUNT * 2 //ignore last two input windows (e.g. 2.days * 2 in steps) for training

        for (offset in 0..trainingWindowSize step trainingTimeStepSize) {
            val expectedTarget = inputMTSD.getSampleOfFeature(
                MultiTimeSeriesDiscrete.FeatureID(
                    TimeSeriesMetric.HEART_RATE,
                    PIDComponent.PROPORTIONAL
                ), offset + (Predictor.TIME_SERIES_SAMPLE_COUNT - 1)
            )

            trainingSamples.add(
                TrainingSample(
                    generateFlattenedMultiExtrapolationResults(inputMTSD, offset),
                    expectedTarget
                )
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
        inputMTSD: MultiTimeSeriesDiscrete,
        indexOffset: Int = 0
    ): List<Double> {
        fun extrapolate(series: DoubleArray, subtractFirst: Boolean = false): List<Double> {
            val extrapolations =
                LinearExtrapolator.multipleExtrapolate(series, indexOffset).extrapolations
            return extrapolations.map { (_, line) ->
                val result = line.getExtrapolationResult()
                if (subtractFirst) result - series[indexOffset] else result
            }
        }

        val flatExtrapolationResults = inputMTSD.getAllFeatureIDs().flatMap { featureID ->
            extrapolate(inputMTSD.getFeatureView(featureID).toDoubleArray())
        }

        return flatExtrapolationResults
    }

    /**
     * Performs regression training on the currently stored training samples to compute
     * the linear coefficients for the prediction model using Tikhonov regularization.
     *
     * @throws IllegalStateException if no training samples have been added.
     */
    fun trainOnStoredSamples() {
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