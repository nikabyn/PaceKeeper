package org.htwk.pacing.backend.predictor.preprocessing

import org.htwk.pacing.backend.predictor.Predictor
import kotlin.math.pow

/**
 * Adjusts a heart rate (HR) value based on the athlete's anaerobic threshold and intensity overload.
 *
 * The function calculates an intensity factor that increases with HR values above the anaerobic threshold
 * and applies it to the raw HR to estimate the effective cardiac load.
 *
 * @param hr The measured heart rate in beats per minute (BPM).
 * @param fixedParameters Fixed parameters including the anaerobic threshold (BPM).
 * @return The adjusted heart rate representing the cardiac load.
 */
fun adjustHR(
    hr: Double,
    fixedParameters: Predictor.FixedParameters
): Double {
    val threshold = fixedParameters.anaerobicThresholdBPM
    val overload = ((hr - threshold) / threshold).coerceAtLeast(0.0)

    val intensityFactor =
        (1.0 + 5 * overload.pow(1.5)).coerceAtMost(1.6)

    val cardiacLoad = hr * intensityFactor
    return cardiacLoad
}

/**
 * Adjusts a heart rate variability (HRV) value based on current heart rate and the athlete's anaerobic threshold.
 *
 * HRV is scaled down if the heart rate exceeds the anaerobic threshold, simulating the physiological
 * reduction of variability under higher cardiac load.
 *
 * @param hr The measured heart rate in beats per minute (BPM).
 * @param hrv The measured heart rate variability.
 * @param fixedParameters Fixed parameters including the anaerobic threshold (BPM).
 * @return The adjusted HRV reflecting the influence of elevated heart rate.
 */
fun adjustHRV(
    hr: Double,
    hrv: Double,
    fixedParameters: Predictor.FixedParameters
): Double {
    val overload = (hr - fixedParameters.anaerobicThresholdBPM).coerceAtLeast(0.0)
    val factor = (1.0 - overload / 100.0).coerceIn(0.5, 1.0)
    return hrv * factor
}