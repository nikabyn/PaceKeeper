package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "validated_energy_level")
data class ValidatedEnergyLevelEntry(
    @PrimaryKey
    val time: Instant,
    val validation: Validation,
    val percentage: Percentage,
) : TimedEntry {
    override val start get() = time
    override val end get() = time
}

enum class Validation(val code: Int) {
    Correct(1),
    Adjusted(0),
}

@Dao
interface ValidatedEnergyLevelDao : TimedSeries<ValidatedEnergyLevelEntry> {
    @Query("delete from validated_energy_level")
    override suspend fun deleteAll()

    @Query("select * from validated_energy_level")
    override suspend fun getAll(): List<ValidatedEnergyLevelEntry>

    @Query("select * from validated_energy_level order by time desc limit 1")
    override suspend fun getLatest(): ValidatedEnergyLevelEntry?

    @Query("select * from validated_energy_level order by time desc limit 1")
    fun getLatestLive(): Flow<ValidatedEnergyLevelEntry>

    @Query("select * from validated_energy_level where time between :begin and :end")
    override suspend fun getInRange(begin: Instant, end: Instant): List<ValidatedEnergyLevelEntry>

    @Query("select null from validated_energy_level")
    override fun getChangeTrigger(): Flow<Int?>
}
