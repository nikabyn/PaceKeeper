package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.htwk.pacing.ui.math.sigmoidStable
import org.jetbrains.kotlinx.multik.api.identity
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.operations.plus
import org.jetbrains.kotlinx.multik.ndarray.operations.times
import org.jetbrains.kotlinx.multik.ndarray.operations.toList
import kotlin.time.Duration.Companion.hours

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


    fun train(regressionInput: IPreprocessor.MultiTimeSeriesDiscrete) {
        require(regressionInput.duration > Predictor.TIME_SERIES_DURATION + 12.hours) //we can only run regression if we have enough data

        //split regressionInput into by 6 elements overlapping windows and determine extrapolations for each window
        val timeSeriesExtrapolationSources = regressionInput.metrics.flatMap {
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

        val lastWindowBeginHour =
            (regressionInput.duration.inWholeHours - Predictor.TIME_SERIES_DURATION.inWholeHours - 6).toInt()

        val allExtrapolationResults: MutableList<List<Double>> = mutableListOf()
        val allExpectedFutureValues: MutableList<Double> = mutableListOf()
        for (hour in 0 until lastWindowBeginHour) {
            val sliceIndices =
                IntRange(
                    (hour * 6),
                    (Predictor.TIME_SERIES_DURATION.inWholeHours.toInt() + hour * 6)
                )

            val flatExtrapolationResults = timeSeriesExtrapolationSources.flatMap { series ->
                LinearExtrapolator.multipleExtrapolate(series.sliceArray(sliceIndices)).extrapolations.map { (_, extrapolationLine) -> extrapolationLine.resultPoint.second }
            }

            allExtrapolationResults.add(flatExtrapolationResults)

            val futureIndex = sliceIndices.last + 2
            //set future expected value target
            val heartRateMetric =
                regressionInput.metrics[IPreprocessor.TimeSeriesMetric.HEART_RATE] as IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
            allExpectedFutureValues.add(
                heartRateMetric.proportional[futureIndex]
            )
        }
        //convert extrapolationResults to matrix
        val historicExtrapolations: NDArray<Double, D2> = mk.ndarray(allExtrapolationResults)
        //convert flatExtrapolationResults to D1Array
        val historicEnergyTargets = mk.ndarray(allExpectedFutureValues)

        linearCoefficients =
            leastSquares(historicExtrapolations, historicEnergyTargets).toList()
    }

    /**
     * Predicts the next energy level data point based on the preprocessed time series data.
     *
     * This function currently uses a simplified model, applying a sigmoid function to the last
     * proportional heart rate value. The final model is intended to use a linear combination

     * of various time series features (e.g., integrals, derivatives) once they are implemented.
     *
     * @param input The [IPreprocessor.MultiTimeSeriesDiscrete] object containing the preprocessed
     *              time series data, such as heart rate.
     * @return A [Double] representing the extrapolated energy level, scaled between 0.0 and 1.0.
     */
    override fun predict(input: IPreprocessor.MultiTimeSeriesDiscrete): Double {
        //TODO: use integral and other derived time series in a linear combination model once they're
        // implemented in the preprocessor
        val heartRateMetric =
            input.metrics[IPreprocessor.TimeSeriesMetric.HEART_RATE] as IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
        return sigmoidStable(scaleParam * heartRateMetric.proportional.last());
    }
}