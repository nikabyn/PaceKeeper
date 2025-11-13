package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.linalg.LinearAlgebraSolver.leastSquaresTikhonov
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toList


object LinearCombinationPredictionModel : IPredictionModel {
    private var linearCoefficients: List<Double> = listOf()

    data class TrainingSample(
        val multiExtrapolations: List<Double>,
        val expectedEnergyLevel: Double
    )

    private val trainingSamples: MutableList<TrainingSample> = mutableListOf()

    private fun generateFlattenedMultiExtrapolationResults(
        input: IPreprocessor.MultiTimeSeriesDiscrete,
    ): List<Double> {
        val timeSeriesExtrapolationSources = input.metrics.mapValues {
            when (val result = it.value) {
                is IPreprocessor.DiscreteTimeSeriesResult.DiscretePID -> listOf(
                    result.proportional,
                    result.integral,
                    result.derivative
                )

                is IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral -> listOf(
                    result.integral
                )
            }
        }

        val indexOffset = 0

        val flatExtrapolationResults = timeSeriesExtrapolationSources.flatMap { (key, series) ->
            val a = when(key) {
                IPreprocessor.TimeSeriesMetric.HEART_RATE -> listOf(
                    LinearExtrapolator.multipleExtrapolate(series[0], indexOffset = indexOffset).extrapolations.map { (_, extrapolationLine) -> extrapolationLine.getExtrapolationResult() },
                    LinearExtrapolator.multipleExtrapolate(series[1], indexOffset = indexOffset).extrapolations.map { (_, extrapolationLine) -> extrapolationLine.getExtrapolationResult() - series[1][0]},
                    LinearExtrapolator.multipleExtrapolate(series[2], indexOffset = indexOffset).extrapolations.map { (_, extrapolationLine) -> extrapolationLine.getExtrapolationResult() - series[2][0] }
                )
                IPreprocessor.TimeSeriesMetric.DISTANCE -> listOf(
                    LinearExtrapolator.multipleExtrapolate(series[0], indexOffset = indexOffset).extrapolations.map { (_, extrapolationLine) -> extrapolationLine.getExtrapolationResult() }
                )
            }.flatten()
            a
        }

        return flatExtrapolationResults
    }

    fun addTrainingSampleFromMultiTimeSeriesDiscrete(
        input: IPreprocessor.MultiTimeSeriesDiscrete,
        expectedEnergyLevel: Double
    ) {
        trainingSamples.add(
            TrainingSample(
                generateFlattenedMultiExtrapolationResults(input),
                expectedEnergyLevel
            )
        )
    }

    fun trainOnStoredSamples() {
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
    override fun predict(input: IPreprocessor.MultiTimeSeriesDiscrete): Double {
        val flattenedExtrapolations = generateFlattenedMultiExtrapolationResults(input)

        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations)
        val coefficientsVector: D1Array<Double> = mk.ndarray(linearCoefficients)

        val prediction = mk.linalg.dot(extrapolationsVector, coefficientsVector)
        return prediction

    }
}