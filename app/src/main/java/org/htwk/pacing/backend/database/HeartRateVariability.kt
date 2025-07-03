package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "heart_rate_variability")
data class HeartRateVariabilityEntry(
    @PrimaryKey
    val time: Instant,
    val variability: Double,
)

@Dao
interface HeartRateVariabilityDao : TimedSeries<HeartRateVariabilityEntry> {
    @Query("delete from heart_rate_variability")
    suspend fun deleteAll()

    @Query("select * from heart_rate_variability")
    suspend fun getAll(): List<HeartRateVariabilityEntry>

    @Query("select * from heart_rate_variability where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<HeartRateVariabilityEntry>


    @Query("select null from heart_rate_variability")
    override fun getChangeTrigger(): Flow<Int?>
}
