package org.htwk.pacing.backend.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow


@Entity(tableName = "mode")
data class ModeEntry(
    @PrimaryKey val id: Int = 0,
    val demo: Boolean = false
)

@Dao
interface ModeDao {
    @Query("SELECT * FROM mode WHERE id = 0")
    fun getModeLive(): Flow<ModeEntry?>

    @Query("SELECT * FROM mode WHERE id = 0")
    suspend fun getMode(): ModeEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMode(mode: ModeEntry)
}

