package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "speed")
data class SpeedEntry(
    @PrimaryKey
    val time: Instant,
    val velocity: Velocity,
)

@Dao
interface SpeedDao : TimedSeries<SpeedEntry> {
    @Query("delete from speed")
    override suspend fun deleteAll()

    @Query("select * from speed")
    override suspend fun getAll(): List<SpeedEntry>

    @Query("select * from speed where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<SpeedEntry>


    @Query("select null from speed")
    override fun getChangeTrigger(): Flow<Int?>
}
