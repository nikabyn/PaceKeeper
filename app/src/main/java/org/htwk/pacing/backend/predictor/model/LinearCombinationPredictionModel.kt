package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor
import org.htwk.pacing.ui.math.sigmoidStable
import org.jetbrains.kotlinx.multik.api.linalg.dot
import org.jetbrains.kotlinx.multik.api.linalg.solve
import org.jetbrains.kotlinx.multik.api.mk
import org.jetbrains.kotlinx.multik.api.ndarray
import org.jetbrains.kotlinx.multik.ndarray.data.D1Array
import org.jetbrains.kotlinx.multik.ndarray.data.D2
import org.jetbrains.kotlinx.multik.ndarray.data.D2Array
import org.jetbrains.kotlinx.multik.ndarray.data.NDArray
import org.jetbrains.kotlinx.multik.ndarray.operations.toList

object LinearCombinationPredictionModel : IPredictionModel {
    val scaleParam = 1.0f; //TODO: replace with non-dummy, dynamic weight/offset parameters
    private var linearCoefficients: List<Double> = listOf()

    fun leastSquares(A: D2Array<Double>, b: D1Array<Double>): D1Array<Double> {
        val transposedA = A.transpose()                                 // (A^T)
        val transposedATimesA = mk.linalg.dot(transposedA, A)   // (A^T) * A
        val transposedATimesb = mk.linalg.dot(transposedA, b)       // (A^T) * b
        val x =
            mk.linalg.solve(
                transposedATimesA,
                transposedATimesb
            )  //Solution of ((A^T)*A)*x = (A^T)*b
        return x
    }

    fun train(regressionInput: IPreprocessor.MultiTimeSeriesDiscrete) {
        require(regressionInput.duration > Predictor.TIME_SERIES_DURATION) //we can only run regression if we have enough data

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