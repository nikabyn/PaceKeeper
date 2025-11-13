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
        indexOffset: Int = 0
    ): List<Double> {
        fun extrapolate(series: DoubleArray, subtractFirst: Boolean = false): List<Double> {
            val extrapolations = LinearExtrapolator.multipleExtrapolate(series, indexOffset).extrapolations
            return extrapolations.map { (_, line) ->
                val result = line.getExtrapolationResult()
                if (subtractFirst) result - series[indexOffset] else result
            }
        }

        val flatExtrapolationResults = input.metrics.flatMap { (key, discreteTimeSeriesResult) ->
            when(key) {
                IPreprocessor.TimeSeriesMetric.HEART_RATE -> {
                    val discretePID = discreteTimeSeriesResult as IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
                    listOf(
                        extrapolate(discretePID.proportional),
                        extrapolate(discretePID.integral, subtractFirst = true),
                        extrapolate(discretePID.derivative)
                    )
                }
                IPreprocessor.TimeSeriesMetric.DISTANCE -> {
                    val discreteIntegral = discreteTimeSeriesResult as IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
                    listOf(extrapolate(discreteIntegral.integral, subtractFirst = true))
                }
            }.flatten()
        }

        return flatExtrapolationResults
    }

    fun addTrainingSamplesFromMultiTimeSeriesDiscrete(
        input: IPreprocessor.MultiTimeSeriesDiscrete,
    ) {
        val heartRateTimeSeries = (input.metrics[IPreprocessor.TimeSeriesMetric.HEART_RATE]!! as IPreprocessor.DiscreteTimeSeriesResult.DiscretePID).proportional
        val timeSeriesSize = 288//heartRateTimeSeries.size -  300


        for(offset in 0..timeSeriesSize step 7) {
            val expected = heartRateTimeSeries[offset + 287 + 12]

            trainingSamples.add(
                TrainingSample(
                    generateFlattenedMultiExtrapolationResults(input, offset),
                    expected
                )
            )
        }
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