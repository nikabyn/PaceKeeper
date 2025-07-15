package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import kotlinx.datetime.Instant

@Entity(tableName = "read_event", primaryKeys = ["time", "record"])
data class ReadEvent(
    val time: Instant,
    val record: String,
)

@Dao
interface ReadEventDao {
    @Insert
    fun insert(event: ReadEvent)

    @Query("select * from read_event")
    suspend fun getAll(): List<ReadEvent>

    @Query("select * from read_event where record == :record")// order by receivedEnd desc limit 1
    suspend fun getOfRecord(record: String): List<ReadEvent>
}