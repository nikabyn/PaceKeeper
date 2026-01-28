package org.htwk.pacing.backend.model2

import android.util.Log
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Fallback for energy prediction when no heart rate data is available.
 *
 * Computes a personalized energy decay rate from historical validated energy entries.
 * When no HR data is present, applies this decay rate from the last known energy value,
 * scaled by elapsed time and time-of-day.
 */
object EnergyDecayFallback {

    private const val TAG = "EnergyDecayFallback"

    // Default decay: 3% per hour (conservative estimate)
    private const val DEFAULT_HOURLY_DECAY = 3.0

    // Minimum consecutive pairs needed for a personalized rate
    private const val MIN_PAIRS_TOTAL = 5
    private const val MIN_PAIRS_PER_BUCKET = 3

    // Filter thresholds for pair distances
    private const val MIN_PAIR_HOURS = 0.1    // ~6 minutes
    private const val MAX_PAIR_HOURS = 12.0

    /**
     * Returns a default decay rate when insufficient data is available.
     */
    fun defaultDecayRate(): DecayRateResult = DecayRateResult(
        averageHourlyDecay = DEFAULT_HOURLY_DECAY,
        morningDecayRate = null,
        afternoonDecayRate = null,
        eveningDecayRate = null,
        nightRecoveryRate = null,
        dataPointsUsed = 0
    )

    /**
     * Computes a personalized decay rate from historical validated energy entries.
     *
     * Algorithm:
     * 1. Sort entries by time, form consecutive pairs
     * 2. Calculate hourly energy change per pair
     * 3. Filter out pairs with too short (< 6min) or too long (> 12h) gaps
     * 4. Group by time-of-day (morning, afternoon, evening, night)
     * 5. Apply IQR filtering against outliers
     * 6. Use median per time bucket and overall
     *
     * @param energyData Validated energy entries (percentage on 0-100 scale)
     * @return Personalized decay rate, or default if insufficient data
     */
    fun computeDecayRate(energyData: List<EnergyDataPoint>): DecayRateResult {
        val sorted = energyData.sortedBy { it.timestamp }
        if (sorted.size < 2) {
            //Log.d(TAG, "Not enough data points for decay rate: ${sorted.size}")
            return defaultDecayRate()
        }

        data class HourlyChange(val changePerHour: Double, val hourOfDay: Int)

        val changes = mutableListOf<HourlyChange>()

        for (i in 0 until sorted.size - 1) {
            val current = sorted[i]
            val next = sorted[i + 1]
            val elapsedHours = (next.timestamp.toEpochMilliseconds() - current.timestamp.toEpochMilliseconds()) / 3_600_000.0

            if (elapsedHours < MIN_PAIR_HOURS || elapsedHours > MAX_PAIR_HOURS) continue

            // Energy change per hour (positive = energy increase, negative = energy decrease)
            val changePerHour = (next.percentage - current.percentage) / elapsedHours

            val midpointMs = (current.timestamp.toEpochMilliseconds() + next.timestamp.toEpochMilliseconds()) / 2
            val midInstant = Instant.fromEpochMilliseconds(midpointMs)
            val hourOfDay = midInstant.toLocalDateTime(TimeZone.currentSystemDefault()).hour

            changes.add(HourlyChange(changePerHour, hourOfDay))
        }

        if (changes.size < MIN_PAIRS_TOTAL) {
            //Log.d(TAG, "Not enough valid pairs for decay rate: ${changes.size} (need $MIN_PAIRS_TOTAL)")
            return defaultDecayRate()
        }

        val morning = changes.filter { it.hourOfDay in 6..11 }.map { it.changePerHour }
        val afternoon = changes.filter { it.hourOfDay in 12..17 }.map { it.changePerHour }
        val evening = changes.filter { it.hourOfDay in 18..21 }.map { it.changePerHour }
        val night = changes.filter { it.hourOfDay in 22..23 || it.hourOfDay in 0..5 }.map { it.changePerHour }

        val allRates = changes.map { it.changePerHour }
        val overallDecay = -Optimizer.median(allRates)

        val result = DecayRateResult(
            averageHourlyDecay = overallDecay.coerceIn(-10.0, 15.0),
            morningDecayRate = if (morning.size >= MIN_PAIRS_PER_BUCKET) -Optimizer.median(morning) else null,
            afternoonDecayRate = if (afternoon.size >= MIN_PAIRS_PER_BUCKET) -Optimizer.median(afternoon) else null,
            eveningDecayRate = if (evening.size >= MIN_PAIRS_PER_BUCKET) -Optimizer.median(evening) else null,
            nightRecoveryRate = if (night.size >= MIN_PAIRS_PER_BUCKET) -Optimizer.median(night) else null,
            dataPointsUsed = changes.size
        )

        //Log.i(TAG, "Computed decay rate: avg=${"%.2f".format(result.averageHourlyDecay)}%/h " + "morning=${result.morningDecayRate?.let { "%.2f".format(it) } ?: "n/a"}, " + "afternoon=${result.afternoonDecayRate?.let { "%.2f".format(it) } ?: "n/a"}, " + "evening=${result.eveningDecayRate?.let { "%.2f".format(it) } ?: "n/a"}, " + "night=${result.nightRecoveryRate?.let { "%.2f".format(it) } ?: "n/a"} " + "from ${changes.size} pairs")

        return result
    }

    fun getDecayForHour(rate: DecayRateResult, hour: Int): Double {
        return when (hour) {
            in 6..11 -> rate.morningDecayRate ?: rate.averageHourlyDecay
            in 12..17 -> rate.afternoonDecayRate ?: rate.averageHourlyDecay
            in 18..21 -> rate.eveningDecayRate ?: rate.averageHourlyDecay
            else -> rate.nightRecoveryRate ?: rate.averageHourlyDecay
        }
    }

    /**
     * Predicts energy using the personalized decay rate when no HR data is available.
     *
     * @param lastEnergy Last known energy value (0.0-1.0 scale)
     * @param lastTime Timestamp of the last known energy value
     * @param now Current time
     * @param decayRate Personalized decay rate (or null for default)
     * @return Pair of (currentEnergy, futureEnergy) as values between 0.0 and 1.0
     */
    fun predictWithDecay(
        lastEnergy: Double,
        lastTime: Instant,
        now: Instant = Clock.System.now(),
        decayRate: DecayRateResult?
    ): Pair<Double, Double> {
        val rate = decayRate ?: defaultDecayRate()
        val elapsedHours = (now.toEpochMilliseconds() - lastTime.toEpochMilliseconds()) / 3_600_000.0

        val currentHour = now.toLocalDateTime(TimeZone.currentSystemDefault()).hour
        val hourlyDecay = getDecayForHour(rate, currentHour) / 100.0  // Convert from 0-100 to 0-1 scale

        val currentDecayed = (lastEnergy - hourlyDecay * elapsedHours).coerceIn(0.0, 1.0)

        val futureHour = (currentHour + 2) % 24
        val futureDecay = getDecayForHour(rate, futureHour) / 100.0
        val futureDecayed = (currentDecayed - futureDecay * 2.0).coerceIn(0.0, 1.0)

        //Log.d(TAG, "Decay fallback: lastEnergy=${"%.2f".format(lastEnergy)}, " + "elapsed=${"%.1f".format(elapsedHours)}h, " + "rate=${"%.2f".format(hourlyDecay * 100)}%/h, " + "now=${"%.2f".format(currentDecayed)}, future=${"%.2f".format(futureDecayed)}")

        return Pair(currentDecayed, futureDecayed)
    }
}
