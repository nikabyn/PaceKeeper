package org.htwk.pacing

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.math.remap
import kotlin.random.Random

fun randomHeartRate(): Flow<Pair<Float, Instant>> = flow {
    while (true) {
        val value = remap(Random.nextFloat(), 0f, 1f, 55f, 107f)
        emit(Pair(value, Clock.System.now()))
        val millis = remap(Random.nextFloat(), 0f, 1f, 10f, 300f)
        delay(millis.toLong())
    }
}