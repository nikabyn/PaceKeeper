package org.htwk.pacing.backend.predictor.model

import org.htwk.pacing.backend.predictor.Predictor
import kotlin.math.exp


object Model {
    val scaleParam = 1.0f;

    //numerically stable sigmoid implementation
    private fun sigmoidStable(x: Double): Double =
        if (x >= 0.0) {
            val z = exp(-x)
            1.0 / (1.0 + z)
        } else {
            val z = exp(x)
            z / (1.0 + z)
        }

    //returns extrapolated energy level point

    fun predict(input: Predictor.MultiTimeSeriesDiscrete): Double {
        return sigmoidStable(scaleParam * input.heartRate.integral.last());
    }
}