/**
 * Data types that store a value based on a unit. (km, km/h, %, °C, etc.)
 */

package org.htwk.pacing.backend.database

import androidx.annotation.FloatRange

class Length(private val lengthMeters: Double) {
    fun inMeters(): Double = lengthMeters
    fun inKilometers(): Double = lengthMeters / 1000.0

    companion object {
        fun meters(value: Double): Length {
            return Length(value)
        }

        fun kilometers(value: Double): Length {
            return Length(value * 1000.0)
        }
    }
}

class Velocity(private val velocityMetersPerSecond: Double) {
    fun inMetersPerSecond(): Double = velocityMetersPerSecond
    fun inKilometersPerHour(): Double = velocityMetersPerSecond * 3.6

    companion object {
        fun metersPerSecond(value: Double): Velocity {
            return Velocity(value)
        }

        fun kilometersPerHour(value: Double): Velocity {
            return Velocity(value / 3.6)
        }
    }
}

class Percentage(private val percentage: Double) {
    fun toDouble(): Double = percentage

    override fun toString(): String {
        return (percentage * 100.0).toString() + "%"
    }

    companion object {
        fun fromDouble(@FloatRange(from = 0.0, to = 1.0) value: Double): Percentage {
            // TODO: Should this really crash the app in production?
            when (value) {
                in 0.0..1.0 -> return Percentage(value)
                else -> throw Exception("Percentage value `$value` not in range 0.0..=1.0")
            }
        }
    }
}

class Temperature(private val temperatureCelsius: Double) {
    fun inCelsius(): Double = temperatureCelsius

    companion object {
        fun celsius(value: Double): Temperature {
            return Temperature(value)
        }
    }
}