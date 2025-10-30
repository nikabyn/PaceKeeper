package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Clock
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.DistanceEntry
import org.htwk.pacing.backend.database.Length
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.Predictor.MultiTimeSeriesList
import kotlin.time.Duration.Companion.hours

object Preprocessor {
    fun run(raw: MultiTimeSeriesList): Predictor.MultiTimeSeriesSamples {
        // 1. Generische Reinigungsfunktion
        fun <T, R : Comparable<R>> cleanData(
            list: List<T>,
            sortKey: (T) -> R,
            isInvalid: (T) -> Boolean,
            replaceInvalid: (T) -> T
        ): Pair<MutableList<T>, Double> {
            var correctionCount = 0 // Zähler für Ersetzung

            val cleanedList = list
                .sortedBy(sortKey)   // zeitlich sortieren
                .distinct()          // Duplikate entfernen
                .map {
                    if (isInvalid(it)) {
                        correctionCount++ // wenn die Werte aus den Wertebereichen rausgehen
                        replaceInvalid(it)
                    } else it
                }
                .toMutableList()

            val correctionRatio = if (cleanedList.isNotEmpty()) {
                correctionCount.toDouble() / cleanedList.size
            } else 0.0

            return Pair(cleanedList, correctionRatio)
        }

        // 2. Herzfrequenz bereinigen
        val (cleanedHeartRates, heartRateCorrectionRatio) = cleanData(
            list = raw.heartRate,
            sortKey = { it.time },
            isInvalid = { it.bpm < 30 || it.bpm > 220 },
            replaceInvalid = { HeartRateEntry(it.time, 0) }
        )

        //3. Distanz bereinigen
        val (cleanedDistances, distancesCorrectionRatio) = cleanData(
            list = raw.distance,
            sortKey = { it.start },
            isInvalid = { it.length.inMeters() < 0 },
            replaceInvalid = { DistanceEntry(it.start, it.end, length = Length(0.0)) }
        )

        // 3. TODO: Datenbereinigung/Datenqualität erhöhen
        //          Zeitliche Reihenfolge sicherstellen (erledigt)
        //          Doppelte Einträge entfernen (erledigt)
        //          Unplausible Werte entfernen -> also 0 setzen und dann count erhöhen (erledigt)
        //          herausfinden, wie oft es gemacht wurde bzw. der Anteil (erledigt)
        //          Starke Sprünge/Artefakte erkennen und auf 0 setzen,
        //          Fehlende Werte (0er) auffüllen die davor entfernt wurden -> Imputation (null)

        // TODO: see other ticket
        return Predictor.MultiTimeSeriesSamples(
            timeStart = Clock.System.now() - 6.hours,
            heartRate = floatArrayOf(),
            distance = floatArrayOf()
        )
    }
}