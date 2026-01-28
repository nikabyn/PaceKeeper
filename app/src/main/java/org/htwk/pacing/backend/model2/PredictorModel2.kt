package org.htwk.pacing.backend.model2

import android.util.Log
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.Percentage
import org.htwk.pacing.backend.database.PredictedEnergyLevelEntryModel2
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import kotlin.math.abs
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Energie-Vorhersage basierend auf Herzrate.
 *
 * TRAINING:
 *   train() calculates (hrLow, hrHigh, drainFactor, recoveryFactor, energyOffset)
 *   from HR data und validated energy levels
 *
 * PREDICTION:
 *   predict() calculates energy in 2h from actual hr
 *
 */
object PredictorModel2 {

    private const val TAG = "PredictorModel2"

    private fun defaultParams() = OptimizationResult(
        hrLow = 60.0,
        hrHigh = 100.0,
        drainFactor = 1.0,
        recoveryFactor = 1.0,
        loss = Double.POSITIVE_INFINITY,
        energyOffset = 0.0
    )

    val TIME_SERIES_DURATION: Duration = 2.hours

    private var trainedParams: OptimizationResult? = null
    private var decayRate: DecayRateResult? = null
    private var sleepConfig = SleepConfig()
    private var energyConfig = EnergyConfig()

    data class PredictionInput(
        val timeStart: Instant,
        val duration: Duration,
        val heartRate: List<HeartRateEntry>,
        val validatedEnergy: List<ValidatedEnergyLevelEntry>
    )

    fun train(
        heartRate: List<HeartRateEntry>,
        energy: List<ValidatedEnergyLevelEntry>
    ) {
        if (heartRate.isEmpty() || energy.isEmpty()) {
            Log.w(TAG, "Insufficient data: HR=${heartRate.size}, Energy=${energy.size}")
            return
        }

        val hrDataPoints = heartRate.map { HRDataPoint(it.time, it.bpm.toDouble()) }
        val energyDataPoints = energy.map {
            EnergyDataPoint(it.time, it.percentage.toDouble() * 100.0, it.validation.name)
        }

        val hrAgg = aggregateHR(hrDataPoints, energyConfig.aggregationMinutes)
        if (hrAgg.size < 10) {
            Log.w(TAG, "Not enough aggregated HR data: ${hrAgg.size}")
            return
        }

        val result = Optimizer.autoFit(
            hrAgg = hrAgg,
            validatedEnergy = energyDataPoints,
            sleepConfig = sleepConfig,
            aggregationMinutes = energyConfig.aggregationMinutes,
            fitRange = FitRange.ALL
        )

        if (result.usedDays > 0) {
            trainedParams = result.result
            decayRate = EnergyDecayFallback.computeDecayRate(energyDataPoints)
            Log.i(TAG, "Training complete: ${result.usedDays}/${result.totalDays} cycles")
        } else {
            Log.w(TAG, "Training failed, using defaults")
            trainedParams = defaultParams()
        }
    }

    /**
     * calculates energy in 2h from actual hr
     */
    fun predict(input: PredictionInput): PredictedEnergyLevelEntryModel2 {
        val params = trainedParams ?: defaultParams()
        val now = input.timeStart + input.duration
        val futureTime = now + 2.hours

        // fallback if there are no hr data
        if (input.heartRate.isEmpty()) {
            val (energyNow, energyFuture) = fallbackEnergy(input.validatedEnergy)
            return PredictedEnergyLevelEntryModel2(
                time = now,
                percentageNow = Percentage(energyNow),
                timeFuture = futureTime,
                percentageFuture = Percentage(energyFuture)
            )
        }

        val hrDataPoints = input.heartRate.map { HRDataPoint(it.time, it.bpm.toDouble()) }
        val energyDataPoints = input.validatedEnergy.map {
            EnergyDataPoint(it.time, it.percentage.toDouble() * 100.0, it.validation.name)
        }

        val hrAgg = aggregateHR(hrDataPoints, energyConfig.aggregationMinutes)
        if (hrAgg.isEmpty()) {
            val (energyNow, energyFuture) = fallbackEnergy(input.validatedEnergy)
            return PredictedEnergyLevelEntryModel2(
                time = now,
                percentageNow = Percentage(energyNow),
                timeFuture = futureTime,
                percentageFuture = Percentage(energyFuture)
            )
        }

        val hrvData = HRVDrain.calculateHRVFromHR(hrDataPoints, windowMinutes = 5)
        val lastValidatedEnergy = energyDataPoints.maxByOrNull { it.timestamp }?.percentage ?: 50.0

        val curve = EnergyCalculation.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrAgg,
            hrvData = hrvData,
            hrLow = params.hrLow,
            hrHigh = params.hrHigh,
            drainFactor = params.drainFactor,
            recoveryFactor = params.recoveryFactor,
            timeOffsetMinutes = energyConfig.timeOffsetMinutes,
            aggregationMinutes = energyConfig.aggregationMinutes,
            validatedPoints = energyDataPoints,
            fallbackStartEnergy = lastValidatedEnergy,
            energyOffset = params.energyOffset
        )

        val energyNow = findEnergyAtTime(curve, now) ?: (lastValidatedEnergy / 100.0)
        val energyFuture = findEnergyAtTime(curve, futureTime)
            ?: curve.lastOrNull()?.energy?.div(100.0)
            ?: (lastValidatedEnergy / 100.0)

        return PredictedEnergyLevelEntryModel2(
            time = now,
            percentageNow = Percentage(energyNow.coerceIn(0.0, 1.0)),
            timeFuture = futureTime,
            percentageFuture = Percentage(energyFuture.coerceIn(0.0, 1.0))
        )
    }

    private fun findEnergyAtTime(curve: List<EnergyResultWithHRV>, targetTime: Instant): Double? {
        if (curve.isEmpty()) return null

        val targetMs = targetTime.toEpochMilliseconds()
        val closest = curve.minByOrNull { abs(it.timestamp.toEpochMilliseconds() - targetMs) }

        val diff = abs((closest?.timestamp?.toEpochMilliseconds() ?: 0) - targetMs)
        return if (diff <= 30 * 60 * 1000) closest?.energy?.div(100.0) else null
    }

    private fun fallbackEnergy(validatedEnergy: List<ValidatedEnergyLevelEntry>): Pair<Double, Double> {
        val last = validatedEnergy.maxByOrNull { it.time }
        val lastEnergy = last?.percentage?.toDouble() ?: 0.5
        val lastTime = last?.time ?: Clock.System.now()
        return EnergyDecayFallback.predictWithDecay(lastEnergy, lastTime, decayRate = decayRate)
    }
    /**
     * Aggregates heart rate data into time buckets.
     */
    fun aggregateHR(
        data: List<HRDataPoint>,
        minutes: Int
    ): List<HRDataPoint> {
        if (data.isEmpty()) return emptyList()

        val buckets = mutableMapOf<Long, MutableList<Double>>()
        val ms = minutes * 60 * 1000L

        for (d in data) {
            val key = (d.timestamp.toEpochMilliseconds() / ms) * ms
            buckets.getOrPut(key) { mutableListOf() }.add(d.bpm)
        }

        return buckets.entries
            .map { (ts, vals) ->
                HRDataPoint(
                    timestamp = Instant.fromEpochMilliseconds(ts),
                    bpm = vals.average()
                )
            }
            .sortedBy { it.timestamp }
    }
}