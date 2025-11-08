package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor

object LinearExtrapolator {

    private val stepsIntoFuture =
        (Predictor.PREDICTION_WINDOW_DURATION.inWholeSeconds / Predictor.TIME_SERIES_STEP_DURATION.inWholeSeconds).toInt()


    data class ExtrapolationStrategy(
        val firstAveragingRange: IntRange,

        val secondAveragingRange: IntRange
    )

    enum class EXTRAPOLATION_STRATEGIES(val strategy: ExtrapolationStrategy) {
        //enum for extrapolation from 3-hour average (6 hours in past) vs 3-hour average (9 hours in past)

        NOW_VS_6_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange(-(6 * 6), 0),
                IntRange(0, 0)
            )
        ),
        NOW_VS_12_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange(-(6 * 6), 0),
                IntRange(0, 0)
            )
        ),
        PAST_6_HOUR_TREND(
            ExtrapolationStrategy(
                IntRange(-(6 * 12), -(-6 * 6)),
                IntRange(0, 0)
            )
        ),


    }

    //TODO: use fibonacci here for optimal logarithmic coverage
    //TODO: rethink shifted lines
    //TODO: use average windows per line, that correspond to trend window we're looking at

    data class MultiExtrapolationResult(
        val extrapolations: List<Double>
    )

    fun linearExtrapolate(x0: Int, y0: Double, x1: Int, y1: Double): Double {
        val slope = (y1 - y0) / (x1 - x0).toDouble()
        return y1 + slope * (x1 + stepsIntoFuture)
    }

    //TODO: value now vs average of window

    fun multipleExtrapolate(timeSeries: DoubleArray): MultiExtrapolationResult {
        val latestValue = timeSeries.last();

        //now vs 6-hour average
        val averageLast6Hours = timeSeries.slice(timeSeries.size - 36..timeSeries.size).average()
        val extrapolation = linearExtrapolate(-18, averageLast6Hours, 0, latestValue)

        val averageLast12Hours = timeSeries.slice(timeSeries.size - 72..timeSeries.size).average()
        val extrapolation2 = linearExtrapolate(-36, averageLast12Hours, 0, latestValue)

        //short-term trend
        val extrapolation3 =
            linearExtrapolate(-3, timeSeries[timeSeries.size - 1 - 3], 0, latestValue)

        return MultiExtrapolationResult(extrapolations = lines.map { line ->
            val startIdx = timeSeries.size - 1 - line.first   //first discrete timepoint of line
            val endIdx = timeSeries.size - 1 - line.second    //second discrete timepoint of line

            val startY = timeSeries[startIdx] //value at first point
            val endY = timeSeries[endIdx] //value at second point

            val slope = (endY - startY) / (endIdx - startIdx) //slope between the points
            val result = endY + slope * (endY + stepsToExtrapolate) //linear trend extrapolation
            result //return the result in this lambda expression
        })
    }
}