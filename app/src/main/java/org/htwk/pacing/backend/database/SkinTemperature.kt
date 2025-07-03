package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "skin_temperature")
data class SkinTemperatureEntry(
    @PrimaryKey
    val time: Instant,
    val temperature: Temperature,
)

@Dao
interface SkinTemperatureDao : TimedSeries<SkinTemperatureEntry> {
    @Query("delete from skin_temperature")
    suspend fun deleteAll()

    @Query("select * from skin_temperature")
    suspend fun getAll(): List<SkinTemperatureEntry>

    @Query("select * from skin_temperature where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<SkinTemperatureEntry>


    @Query("select null from skin_temperature")
    override fun getChangeTrigger(): Flow<Int?>
}
