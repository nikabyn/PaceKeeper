package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Entity(tableName = "steps")
data class StepsEntry(
    @PrimaryKey
    val start: Instant,
    val end: Instant,
    val count: Long,
)

@Dao
interface StepsDao : TimedSeries<StepsEntry> {
    @Query("delete from steps")
    override suspend fun deleteAll()

    @Query("select * from steps")
    override suspend fun getAll(): List<StepsEntry>

    @Query("""select * from steps where "start" <= :end and "end" >= :begin""")
    override suspend fun getInRange(begin: Instant, end: Instant): List<StepsEntry>

    @Query("select null from steps")
    override fun getChangeTrigger(): Flow<Int?>
}
