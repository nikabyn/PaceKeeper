package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "heart_rate")
data class HeartRateEntry(
    @PrimaryKey
    val time: Instant,
    val bpm: Long,
) : TimedEntry {
    override val start get() = time
    override val end get() = time
}

@Dao
interface HeartRateDao : TimedSeries<HeartRateEntry> {
    @Query("delete from heart_rate")
    override suspend fun deleteAll()

    @Query("select * from heart_rate")
    override suspend fun getAll(): List<HeartRateEntry>

    @Query("select * from heart_rate order by time desc limit 1")
    override suspend fun getLatest(): HeartRateEntry?

    @Query("select * from heart_rate where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<HeartRateEntry>

    @Query("select null from heart_rate")
    override fun getChangeTrigger(): Flow<Int?>
}
