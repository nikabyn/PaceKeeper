package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Clock
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesList
import kotlin.time.Duration.Companion.hours

object Preprocessor {
    fun run(raw: MultiTimeSeriesList): Predictor.MultiTimeSeriesSamples {
        //TODO: see other ticket
        return Predictor.MultiTimeSeriesSamples(
            timeStart = Clock.System.now() - 6.hours,
            heartRate = floatArrayOf(),
            distance = floatArrayOf()
        )
    }
}