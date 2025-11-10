package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.predictor.Predictor.FixedParameters
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesEntries
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.ui.math.discreteDerivative
import org.htwk.pacing.ui.math.discreteTrapezoidalIntegral

/**
 * The IPreprocessor interface defines the contract for preprocessing raw time series data.
 *
 * Preprocessors are responsible for taking raw, potentially noisy, and irregularly sampled time series data
 * (like heart rate and distance) and transforming it into a clean, uniformly sampled format suitable for
 * a prediction model. This process involves cleaning, resampling, and deriving features like integrals
 * and derivatives.
 */
interface IPreprocessor {

    /**
     * A sealed interface representing the results of preprocessing on a single time series.
     * It provides different data structures for continuous and aggregated time series.
     */
    sealed interface DiscreteTimeSeriesResult {

        /**
         * Represents preprocessed data for a continuous time series, such as heart rate.
         * It includes the proportional (original), integral, and derivative components.
         * @property proportional The cleaned and resampled time series data ("P" part of PID).
         * @property integral The discrete trapezoidal integral of the proportional data ("I" part of PID).
         * @property derivative The discrete derivative of the proportional data ("D" part of PID).
         */
        data class DiscretePID(
            val proportional: DoubleArray, // "P" part of PID
            val integral: DoubleArray, // "I" part PID
            val derivative: DoubleArray // "D" part of PID
        ) : DiscreteTimeSeriesResult {
            companion object {
                fun from(proportionalInput: DoubleArray) = DiscretePID(
                    proportionalInput,
                    proportionalInput.discreteTrapezoidalIntegral(),
                    proportionalInput.discreteDerivative()
                )
            }
        }

        /**
         * Represents preprocessed data for an aggregated or summable time series, such as distance.
         * It primarily contains the integral component.
         * @property integral The discrete trapezoidal integral of the time series data.
         */
        data class DiscreteIntegral(
            val integral: DoubleArray
        ) : DiscreteTimeSeriesResult {
            companion object {
                fun from(proportionalInput: DoubleArray) = DiscreteIntegral(
                    proportionalInput.discreteTrapezoidalIntegral()
                )
            }
        }
    }

    /**
     * A generic container for a single time series before preprocessing.
     * @property timeStart The starting timestamp of the time series.
     * @property data The list of timed data points.
     * @property type The type of the time series (continuous or aggregated).
     */
    data class GenericTimeSeriesEntries(
        val timeStart: Instant,
        val data: List<GenericTimedDataPoint>,
        val type: TimeSeriesType,
    ) {
        /**
         * Enum to differentiate between time series types.
         * see ui#38 for explanation of "classes" https://gitlab.dit.htwk-leipzig.de/pacing-app/ui/-/issues/38#note_248963
         */
        enum class TimeSeriesType {
            /** For values that change continuously over time, like heart rate. */
            CONTINUOUS,

            /** For values that accumulate over time, like total steps or distance. */
            AGGREGATED,
        }
    }


    /**
     * A container for multiple, preprocessed, and discrete time series, ready for the model.
     * This is an internal data structure used between the preprocessor and the model.
     * @property timeStart The common starting timestamp for all contained time series.
     * @property heartRate The preprocessed [DiscretePID] for heart rate.
     * @property distance The preprocessed [DiscreteIntegral] for distance.
     */
    data class MultiTimeSeriesDiscrete(
        val timeStart: Instant,

        //will be expanded with more vitals
        //class 1 (continuous values)
        val heartRate: DiscretePID,
        //class 2 (aggregated values)
        val distance: DiscreteIntegral
    )

    /**
     * Holds data quality metrics calculated during preprocessing.
     * @property cleanedHeartRatesRatio The percentage of heart rate data points remaining after cleaning.
     * @property cleanedDistancesRatio The percentage of distance data points remaining after cleaning.
     */
    data class QualityRatios(
        val cleanedHeartRatesRatio: Percentage,
        val cleanedDistancesRatio: Percentage
    )

    /**
     * Executes the preprocessing pipeline.
     *
     * @param raw The raw, multi-series input data from the predictor.
     * @param fixedParameters The fixed parameters for the prediction, used for resampling configuration.
     * @return A [MultiTimeSeriesDiscrete] object containing the cleaned, resampled, and feature-engineered data.
     */
    fun run(
        raw: MultiTimeSeriesEntries,
        fixedParameters: FixedParameters
    ): MultiTimeSeriesDiscrete
}