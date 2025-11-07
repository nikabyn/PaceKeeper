package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor

object LinearExtrapolator {

    private val lineIntervals = listOf(0, 3, 6, 12, 24, 36, 72);

    private val lines: List<Pair<Int, Int>> =
        lineIntervals.map { Pair(it, 0) } +                 // (3,0), (6,0), ...
        lineIntervals.zipWithNext { a, b -> Pair(b, a) }    // (6,3), (12,6), ..
    /*lineSpacingSequence.flatMapIndexed { i, a ->
        lineSpacingSequence.drop(i + 1).map { b -> b to a }
    }*/





    //TODO: use fibonacci here for optimal logarithmic coverage
            //TODO: add shifted lines
            //TODO: use average windows per line
        )

    data class MultiExtrapolationResult(
        val extrapolations: List<Double>
    )

    fun multipleExtrapolate(timeSeries: DoubleArray): MultiExtrapolationResult {
        val stepsToExtrapolate =
            (Predictor.PREDICTION_WINDOW_DURATION.inWholeSeconds / Predictor.TIME_SERIES_STEP_DURATION.inWholeSeconds).toInt()

        return MultiExtrapolationResult(extrapolations = lines.map { line ->
            val startIdx = timeSeries.size - 1 - line.first
            val endIdx = timeSeries.size - 1 - line.second
            val window = endIdx - startIdx

            val startValue = timeSeries[startIdx]
            val endValue = timeSeries[endIdx]

            val slope = (endValue - startValue) / (endIdx - startIdx)
            val result = endValue + slope * (endValue + stepsToExtrapolate)
            result
        })
    }
}