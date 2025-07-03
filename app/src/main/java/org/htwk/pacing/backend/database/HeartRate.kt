package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "heart_rate")
data class HeartRateEntry(
    @PrimaryKey
    val time: Instant,
    val bpm: Long,
)

@Dao
interface HeartRateDao : TimedSeries<HeartRateEntry> {
    @Insert
    suspend fun insert(entry: HeartRateEntry)

    @Insert
    suspend fun insertMany(entries: List<HeartRateEntry>)

    @Query("delete from heart_rate")
    suspend fun deleteAll()

    @Query("select * from heart_rate")
    suspend fun getAll(): List<HeartRateEntry>

    @Query("select * from heart_rate where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<HeartRateEntry>


    @Query("select null from heart_rate")
    override fun getChangeTrigger(): Flow<Int?>
}
