package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor

object LinearExtrapolator {

    private val lineIntervals = listOf(0, 3, 6, 12, 24, 36, 72);

    private val lines: List<Pair<Int, Int>> =
        lineIntervals.map { Pair(it, 0) } +                 // (3,0), (6,0), ...
                lineIntervals.zipWithNext { a, b -> Pair(b, a) }    // (6,3), (12,6), ..

    //TODO: use fibonacci here for optimal logarithmic coverage
    //TODO: rethink shifted lines
    //TODO: use average windows per line, that correspond to trend window we're looking at

    data class MultiExtrapolationResult(
        val extrapolations: List<Double>
    )

    fun multipleExtrapolate(timeSeries: DoubleArray): MultiExtrapolationResult {
        val stepsToExtrapolate =
            (Predictor.PREDICTION_WINDOW_DURATION.inWholeSeconds / Predictor.TIME_SERIES_STEP_DURATION.inWholeSeconds).toInt()

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