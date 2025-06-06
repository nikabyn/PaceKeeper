package org.htwk.pacing

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

fun randomHeartRate(avgDelayMs: Int): Flow<Pair<Double, Instant>> = flow {
    val timeZone = TimeZone.currentSystemDefault()
    val start = LocalDateTime.parse("2025-01-01T00:00").toInstant(timeZone)
    var time = start

    while (time - start < 24.hours) {
        val value = Random.nextDouble(55.0, 107.0)
        emit(Pair(value, time))
        time += 15.minutes
        val millis = Random.nextDouble(avgDelayMs * 0.5, avgDelayMs * 1.5)
        delay(millis.toLong())
    }
}