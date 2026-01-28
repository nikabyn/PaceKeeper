package org.htwk.pacing.backend

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.toDuration
import kotlin.time.DurationUnit

/**
 * A central time provider that can be offset to simulate different points in time.
 */
object GlobalTime {
    private val offsetMillis = AtomicLong(0)
    
    // Flow that emits when time offset changes - use this as trigger in prediction jobs
    private val _offsetFlow = MutableStateFlow(0L)
    val offsetFlow = _offsetFlow.asStateFlow()

    fun now(): Instant {
        return Clock.System.now().plus(offsetMillis.get().toMillisDuration())
    }

    fun setTime(target: Instant) {
        val current = Clock.System.now()
        val diff = target.toEpochMilliseconds() - current.toEpochMilliseconds()
        offsetMillis.set(diff)
        _offsetFlow.value = diff // Trigger observers
    }

    
    private fun Long.toMillisDuration(): kotlin.time.Duration = this.toDuration(DurationUnit.MILLISECONDS)
}
