package org.htwk.pacing.backend.database

import androidx.annotation.IntRange
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant


@Entity(tableName = "sleep_session")
data class SleepSessionEntry(
    @PrimaryKey
    override val start: Instant,
    override val end: Instant,
    val stage: SleepStage,
) : TimedEntry

@Dao
interface SleepSessionDao : TimedSeries<SleepSessionEntry> {
    @Query("delete from sleep_session")
    override suspend fun deleteAll()

    @Query("select * from sleep_session")
    override suspend fun getAll(): List<SleepSessionEntry>

    @Query("select * from sleep_session order by `end` desc limit 1")
    override suspend fun getLatest(): SleepSessionEntry?

    @Query("select * from sleep_session where `start` <= :end and `end` >= :begin")
    override suspend fun getInRange(begin: Instant, end: Instant): List<SleepSessionEntry>

    @Query("select null from sleep_session")
    override fun getChangeTrigger(): Flow<Int?>
}


enum class SleepStage(private val stage: Int) {
    Unknown(0),
    Awake(1),
    Sleeping(2),
    OutOfBed(3),
    Light(4),
    Deep(5),
    REM(6),
    AwakeInBed(7);

    fun toInt(): Int = stage

    companion object {
        fun fromInt(@IntRange(from = 0, to = 7) value: Int): SleepStage =
            when (value) {
                0 -> Unknown
                1 -> Awake
                2 -> Sleeping
                3 -> OutOfBed
                4 -> Light
                5 -> Deep
                6 -> REM
                7 -> AwakeInBed
                else -> throw RuntimeException(
                    "Invalid value `$value` for SleepStage, must be in range 0..=7"
                )
            }
    }
}
