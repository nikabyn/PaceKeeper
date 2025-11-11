package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toList

object LinearCombinationPredictionModel : IPredictionModel {
    val scaleParam = 1.0f; //TODO: replace with non-dummy, dynamic weight/offset parameters
    private var linearCoefficients: List<Double> = listOf()

    /**
     * Calculates the solution to the least squares problem Ax = b.
     * This method finds the vector x that minimizes the Euclidean 2-norm ||Ax - b||^2.
     * To solve for x, the normal equation (A^T * A)x = A^T * b is used.
     * To improve numerical stability and prevent overfitting, this implementation uses Tikhonov regularization (also known as ridge regression).
     * A small regularization term (lambda * I) is added to the A^T * A matrix, making the equation
     * (A^T * A + lambda * I)x = A^T * b. This ensures the matrix is invertible.
     *
     * @param A The design matrix (m x n), where m is the number of observations and n is the number of features.
     * @param b The vector of observed values (m-dimensional).
     * @param regularization The regularization parameter (lambda), a small positive value to ensure the matrix is well-conditioned.
     * @return The vector x (n-dimensional) that represents the least squares solution, typically the coefficients of the linear model.
     */
    fun leastSquares(
        A: D2Array<Double>,
        b: D1Array<Double>,
        regularization: Double = 1e-6
    ): D1Array<Double> {
        //we solve this system of equations using the least squares method.
        //the normal equation for least squares is (A^T * A)x = A^T * b.
        //firstly, we compute the components of this equation.

        //transpose the matrix A to get A^T.
        val matrixAt = A.transpose()
        //compute the matrix-matrix product A^T * A.
        val matrixAtA = mk.linalg.dot(matrixAt, A)
        //compute matrix-vector product A^T * b.
        val vectorAtb = mk.linalg.dot(matrixAt, b)

        //to improve numerical stability and prevent issues with singular or ill-conditioned matrices,
        //apply Tikhonov regularization (also known as ridge regression).
        //this involves adding a small multiple of the identity matrix (lambda * I) to A^T * A.
        //this ensures that the matrix (A^T * A + lambda * I) is invertible.
        val n = matrixAtA.shape[0]
        val regularizedAtA = matrixAtA + mk.identity<Double>(n) * regularization

        //solve regularized system of linear equations (A^T * A + lambda * I)x = A^T * b for x.
        return mk.linalg.solve(regularizedAtA, vectorAtb)
    }

    data class TrainingSample(
        val multiExtrapolations: List<Double>,
        val expectedEnergyLevel: Double
    )

    private val trainingSamples: MutableList<TrainingSample> = mutableListOf()

    private fun generateFlattenedMultiExtrapolationResults(
        input: IPreprocessor.MultiTimeSeriesDiscrete,
    ): List<Double> {
        val timeSeriesExtrapolationSources = input.metrics.flatMap {
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

        val flatExtrapolationResults = timeSeriesExtrapolationSources.flatMap { series ->
            LinearExtrapolator.multipleExtrapolate(series).extrapolations.map { (_, extrapolationLine) -> extrapolationLine.resultPoint.second }
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

        //convert extrapolationResults to matrix
        val historicExtrapolations: NDArray<Double, D2> = mk.ndarray(allExtrapolations)
        print("historicExtrapolations:")
        print(historicExtrapolations)

        //convert flatExtrapolationResults to D1Array
        val historicEnergyTargets: NDArray<Double, D1> = mk.ndarray(allExpectedFutureValues)
        print("historicEnergyTargets:")
        print(historicEnergyTargets)

        linearCoefficients =
            leastSquares(historicExtrapolations, historicEnergyTargets).toList()
    }

    /**
     * Predicts the next energy level data point based on the preprocessed time series data.
     * This model uses a linear combination of various time series features (e.g., integrals, derivatives)
     * that have been extrapolated into the future.
     *
     * The prediction is calculated as the dot product of the flattened extrapolation results vector
     * and the learned linear coefficients vector.
     *
     * @param input The [IPreprocessor.MultiTimeSeriesDiscrete] object containing the preprocessed
     *              time series data, such as heart rate.
     * @return A [Double] representing the predicted energy level.
     */
    override fun predict(input: IPreprocessor.MultiTimeSeriesDiscrete): Double {
        val flattenedExtrapolations = generateFlattenedMultiExtrapolationResults(input)

        /*val testTimeSeries =
            (input.metrics[IPreprocessor.TimeSeriesMetric.HEART_RATE]!! as IPreprocessor.DiscreteTimeSeriesResult.DiscretePID).proportional
        val testExtrapolations =
            LinearExtrapolator.multipleExtrapolate(testTimeSeries).extrapolations
        plotTimeSeriesExtrapolationsWithPython(testTimeSeries, testExtrapolations)*/

        // Convert lists to NDArrays for the dot product operation.
        val extrapolationsVector: D1Array<Double> = mk.ndarray(flattenedExtrapolations)
        val coefficientsVector: D1Array<Double> = mk.ndarray(linearCoefficients)

        val prediction = mk.linalg.dot(extrapolationsVector, coefficientsVector)
        return prediction

    }
}