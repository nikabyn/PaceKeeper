package org.htwk.pacing.backend.database

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

sealed interface TimedSeries<E : TimedEntry> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: E)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertMany(entries: List<E>)

    suspend fun deleteAll()

    suspend fun getAll(): List<E>

    suspend fun getLatest(): E?

    suspend fun getInRange(begin: Instant, end: Instant): List<E>

    /**
     * Emits `null` every time the data in the table changes.
     */
    fun getChangeTrigger(): Flow<Int?>

    fun getInRangeLive(begin: Instant, end: Instant): Flow<List<E>> =
        getChangeTrigger().map { getInRange(begin, end) }
}

sealed interface TimedEntry {
    val start: Instant
    val end: Instant
}