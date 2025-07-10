package org.htwk.pacing.backend.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.datetime.Instant

@Database(
    entities = [
        DistanceEntry::class,
        ElevationGainedEntry::class,
        EnergyLevelEntry::class,
        FeelingEntry::class,
        HeartRateEntry::class,
        HeartRateVariabilityEntry::class,
        MenstruationPeriodEntry::class,
        OxygenSaturationEntry::class,
        SkinTemperatureEntry::class,
        SleepSessionEntry::class,
        SpeedEntry::class,
        StepsEntry::class,
        Symptom::class,
        SymptomForFeeling::class,
    ],

    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class PacingDatabase : RoomDatabase() {
    abstract fun distanceDao(): DistanceDao
    abstract fun elevationGainedDao(): ElevationGainedDao
    abstract fun heartRateDao(): HeartRateDao
    abstract fun heartRateVariabilityDao(): HeartRateVariabilityDao
    abstract fun manualSymptomDao(): ManualSymptomDao
    abstract fun menstruationPeriodDao(): MenstruationPeriodDao
    abstract fun oxygenSaturationDao(): OxygenSaturationDao
    abstract fun skinTemperatureDao(): SkinTemperatureDao
    abstract fun sleepSessionsDao(): SleepSessionDao
    abstract fun speedDao(): SpeedDao
    abstract fun stepsDao(): StepsDao
    abstract fun energyLevelDao(): EnergyLevelDao
}

class Converters {
    @TypeConverter
    fun fromInstant(value: Instant): Long = value.toEpochMilliseconds()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.fromEpochMilliseconds(value)

    @TypeConverter
    fun fromLength(value: Length): Double = value.inMeters()

    @TypeConverter
    fun toLength(value: Double): Length = Length.meters(value)

    @TypeConverter
    fun fromVelocity(value: Velocity): Double = value.inMetersPerSecond()

    @TypeConverter
    fun toVelocity(value: Double): Velocity = Velocity.metersPerSecond(value)

    @TypeConverter
    fun fromPercentage(value: Percentage): Double = value.toDouble()

    @TypeConverter
    fun toPercentage(value: Double): Percentage = Percentage.fromDouble(value)

    @TypeConverter
    fun fromSleepStage(value: SleepStage): Int = value.toInt()

    @TypeConverter
    fun toSleepStage(value: Int): SleepStage = SleepStage.fromInt(value)

    @TypeConverter
    fun fromTemperature(value: Temperature): Double = value.inCelsius()

    @TypeConverter
    fun toTemperature(value: Double): Temperature = Temperature.celsius(value)

    @TypeConverter
    fun fromFeeling(value: Feeling): Int = value.level

    @TypeConverter
    fun toFeeling(value: Int): Feeling = Feeling.entries.first { it.level == value }
}
