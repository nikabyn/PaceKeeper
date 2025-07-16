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
) : TimedEntry {
    override val start get() = time
    override val end get() = time
}

@Dao
interface SkinTemperatureDao : TimedSeries<SkinTemperatureEntry> {
    @Query("delete from skin_temperature")
    override suspend fun deleteAll()

    @Query("select * from skin_temperature")
    override suspend fun getAll(): List<SkinTemperatureEntry>

    @Query("select * from skin_temperature order by time desc limit 1")
    override suspend fun getLatest(): SkinTemperatureEntry?

    @Query("select * from skin_temperature where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<SkinTemperatureEntry>


    @Query("select null from skin_temperature")
    override fun getChangeTrigger(): Flow<Int?>
}
