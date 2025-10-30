package org.htwk.pacing.backend.predictor.preprocessing


import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.ui.math.roundInstantToResolution
import java.time.Instant
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object Preprocessor {

    fun run(raw: Predictor.MultiTimeSeriesList): Predictor.MultiTimeSeriesSamples {
        val timeStart = kotlinx.datetime.Clock.System.now() - 6.hours

        //HeartRate 10 min Buckets --> Durchschnitt von Bucket
        val groupedHR = raw.heartRate.groupBy { entry ->
            roundInstantToResolution(entry.time, 10.minutes)
        }

        val hrAveragesPer10Min = groupedHR.mapValues { (_, entries) ->
            entries.map { it.bpm.toFloat() }.average().toFloat()
        }

        val sortedKeys = hrAveragesPer10Min.keys.sorted()
        val heartRateArray = FloatArray(sortedKeys.size)
        var lastKnownHR = hrAveragesPer10Min[sortedKeys.first()] ?: 0f
        for ((i, key) in sortedKeys.withIndex()) {
            val avg = hrAveragesPer10Min[key]

            //fehlende Intervalle werden mit letztem bekannten wert aufgefüllt --> evtl. nicht optimal
            //durchschnitt zwischen letztem bekannten und nächtstem wert wäre besser
            if (avg != null) lastKnownHR = avg
            heartRateArray[i] = lastKnownHR
        }




        //Distance 10-Minuten-Buckets
        val groupedDistance = raw.distance.groupBy { entry ->
            roundInstantToResolution(entry.start, 10.minutes)
        }

        val distancePer10Min = groupedDistance.mapValues { (_, entries) ->
            entries.sumOf { it.length.inMeters()} // Gesamtstrecke pro Intervall
        }

        val distanceArray = FloatArray(sortedKeys.size)
        for ((i, key) in sortedKeys.withIndex()) {
            val value = distancePer10Min[key]
            distanceArray[i] = value!!.toFloat()
        }

        return Predictor.MultiTimeSeriesSamples(
            timeStart = timeStart,
            heartRate = heartRateArray,
            distance = distanceArray
        )
    }

    private fun interpolateMissingValues(values: Map<Instant, Float>): FloatArray {
        val sortedKeys = values.keys.sorted()
        val result = FloatArray(sortedKeys.size)
        val knownValues = sortedKeys.map { key -> values[key] }

        for (i in sortedKeys.indices) {
            val current = knownValues[i]
            if (current != null) {
                result[i] = current
            } else {
                val leftIndex = (i downTo 0).firstOrNull { knownValues[it] != null }
                val rightIndex = (i until knownValues.size).firstOrNull { knownValues[it] != null }
                val left = leftIndex?.let { knownValues[it]!! }
                val right = rightIndex?.let { knownValues[it]!! }

                result[i] = if (left != null && right != null && leftIndex != rightIndex) {
                    val t = (i - leftIndex).toFloat() / (rightIndex - leftIndex)
                    left + t * (right - left)
                } else {
                    left ?: right ?: 0f
                }
            }
        }
        return result
    }
}