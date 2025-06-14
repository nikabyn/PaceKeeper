package org.htwk.pacing.backend.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.datetime.Instant

@Database(entities = [HeartRateEntry::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class PacingDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao

    companion object {
        @Volatile
        private var instance: PacingDatabase? = null

        /**
         * Initializes database or gets existing instance.
         */
        fun getInstance(context: Context): PacingDatabase {
            return instance ?: synchronized(this) {
                Room.databaseBuilder(context, PacingDatabase::class.java, "pacing.db")
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
            }.also { newInstance ->
                instance = newInstance
            }
        }
    }
}

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)
}
