package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "distance")
data class DistanceEntry(
    @PrimaryKey
    override val start: Instant,
    override val end: Instant,
    val length: Length,
) : TimedEntry

@Dao
interface DistanceDao : TimedSeries<DistanceEntry> {
    @Query("delete from distance")
    override suspend fun deleteAll()

    @Query("select * from distance")
    override suspend fun getAll(): List<DistanceEntry>

    @Query("select * from distance order by `end` desc limit 1")
    override suspend fun getLatest(): DistanceEntry?

    @Query("select * from distance where `start` <= :end and `end` >= :begin")
    override suspend fun getInRange(begin: Instant, end: Instant): List<DistanceEntry>

    @Query("select null from distance")
    override fun getChangeTrigger(): Flow<Int?>
}
