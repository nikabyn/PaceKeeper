package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.predictor.Predictor
import kotlin.time.Duration.Companion.minutes

/**
 * Generisches Interface für ein Fallback-System.
 */
interface FallbackHandler<T> {
    fun ensureData(raw: List<T>, timeStart: Instant): List<T>
}

/**
 * Fallback für Herzfrequenzdaten.
 */
object HeartRateFallback : FallbackHandler<HeartRateEntry> {

    override fun ensureData(raw: List<HeartRateEntry>, timeStart: Instant): List<HeartRateEntry> {
        // 1) Live-Modus: Prüfen auf gültige Werte
        val live = raw.filter { it.bpm in 30..220 }
        if (live.isNotEmpty()) return live

        // 2) Fallback: historische Daten aus der DB laden
        val history = loadHistoricalHeartRateData(timeStart)
        if (history.isNotEmpty()) return history

        // 3) Default-Werte
        val default = generateDefaultHeartRateSeries(timeStart)
        if (default.isNotEmpty()) return default

        // 4) Kein Weg gefunden → Fehler
        throw IllegalStateException("Keine Herzfrequenzdaten verfügbar – keine Prognose möglich.")
    }

    private fun loadHistoricalHeartRateData(start: Instant): List<HeartRateEntry> {
        // TODO: Datenbankabfrage implementieren
        return emptyList()
    }

    private fun generateDefaultHeartRateSeries(start: Instant): List<HeartRateEntry> {
        val step = 10.minutes
        val points = (Predictor.TIME_SERIES_DURATION / step).toInt()
        val defaultBpm = 75
        return List(points) { i -> HeartRateEntry(start + (i * step), defaultBpm) }
    }
}

/**
 * Fallback für Distanzdaten.
 */
object DistanceFallback : FallbackHandler<DistanceEntry> {

    override fun ensureData(raw: List<DistanceEntry>, timeStart: Instant): List<DistanceEntry> {
        // 1) Live-Modus: gültige Distanzwerte
        val live = raw.filter { it.length.inMeters() >= 0.0 }
        if (live.isNotEmpty()) return live

        // 2) Fallback: historische Distanzdaten aus der DB
        val history = loadHistoricalDistanceData(timeStart)
        if (history.isNotEmpty()) return history

        // 3) Default-Werte (z. B. 0 Meter)
        val default = generateDefaultDistanceSeries(timeStart)
        if (default.isNotEmpty()) return default

        // 4) Kein Weg gefunden → Fehler
        throw IllegalStateException("Keine Distanzdaten verfügbar – keine Prognose möglich.")
    }

    private fun loadHistoricalDistanceData(start: Instant): List<DistanceEntry> {
        // TODO: Datenbankabfrage implementieren
        return emptyList()
    }

    private fun generateDefaultDistanceSeries(start: Instant): List<DistanceEntry> {
        val step = 10.minutes
        val points = (Predictor.TIME_SERIES_DURATION / step).toInt()
        val defaultLength = 0.0
        return List(points) { i -> DistanceEntry(start + (i * step), start + ((i+1) * step), org.htwk.pacing.backend.database.Length(defaultLength)) }
    }
}
