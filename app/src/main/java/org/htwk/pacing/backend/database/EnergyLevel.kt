package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "energy_level")
data class EnergyLevelEntry(
    @PrimaryKey
    val time: Instant,
    val percentage: Percentage,
)

@Dao
interface EnergyLevelDao : TimedSeries<EnergyLevelEntry> {
    @Query("delete from energy_level")
    override suspend fun deleteAll()

    @Query("select * from energy_level")
    override suspend fun getAll(): List<EnergyLevelEntry>

    @Query("select * from energy_level where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<EnergyLevelEntry>


    @Query("select null from energy_level")
    override fun getChangeTrigger(): Flow<Int?>
}
