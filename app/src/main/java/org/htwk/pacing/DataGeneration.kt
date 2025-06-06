package org.htwk.pacing

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.math.remap
import kotlin.random.Random

fun randomHeartRate(avgDelayMs: Int): Flow<Pair<Float, Instant>> = flow {
    while (true) {
        val value = remap(Random.nextFloat(), 0f, 1f, 55f, 107f)
        emit(Pair(value, Clock.System.now()))
        val millis = remap(Random.nextFloat(), 0f, 1f, avgDelayMs * 0.5f, avgDelayMs * 1.5f)
        delay(millis.toLong())
    }
}