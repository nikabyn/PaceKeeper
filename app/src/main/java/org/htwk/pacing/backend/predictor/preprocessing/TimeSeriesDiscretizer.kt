package org.htwk.pacing.backend.predictor.preprocessing

import kotlinx.datetime.Instant
import org.htwk.pacing.backend.predictor.Predictor
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscreteIntegral
import org.htwk.pacing.backend.predictor.preprocessing.IPreprocessor.DiscreteTimeSeriesResult.DiscretePID
import org.htwk.pacing.ui.math.roundInstantToResolution
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

object TimeSeriesDiscretizer {
    fun run_1_discretize(raw: Predictor.MultiTimeSeriesEntries): IPreprocessor.MultiTimeSeriesDiscrete {
        val timeStart = kotlinx.datetime.Clock.System.now() - 6.hours

        //HeartRate 10 min Buckets --> Durchschnitt von Bucket
        val groupedHR = raw.heartRate.groupBy { entry ->
            roundInstantToResolution(entry.time, 10.minutes)
        }

        val hrAveragesPer10Min = groupedHR.mapValues { (_, entries) ->
            entries.map { it.bpm.toDouble() }.average()
        }

        val sortedKeys = hrAveragesPer10Min.keys.sorted()
        val heartRateArray = DoubleArray(sortedKeys.size)
        var lastKnownHR: Double = hrAveragesPer10Min[sortedKeys.first()] ?: 0.0
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
            entries.sumOf { it.length.inMeters() } // Gesamtstrecke pro Intervall
        }

        val distanceArray = DoubleArray(sortedKeys.size)
        for ((i, key) in sortedKeys.withIndex()) {
            val value = distancePer10Min[key]
            distanceArray[i] = value!!
        }

        return IPreprocessor.MultiTimeSeriesDiscrete(
            timeStart = timeStart,
            heartRate = DiscretePID(heartRateArray, doubleArrayOf(), doubleArrayOf()),
            distance = DiscreteIntegral(distanceArray)
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