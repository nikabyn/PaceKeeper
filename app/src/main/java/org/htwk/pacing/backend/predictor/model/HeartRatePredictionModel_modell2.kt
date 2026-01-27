package org.htwk.pacing.backend.predictor.model

import android.util.Log
import org.htwk.pacing.backend.database.HeartRateEntry
import org.htwk.pacing.backend.database.ValidatedEnergyLevelEntry
import org.htwk.pacing.backend.modell2.*

/**
 * Heart Rate based prediction model using the optimizer algorithm.
 * Translated from TypeScript optimizer.ts
 *
 * Best configuration (from evaluation):
 * - Bereich=all, Methode=median, AutoFit=true, Offset=true, HRV=true, Anchor=true
 * - RMSE=15.48, MAE=11.55, Korr=0.349, Bias=2.44
 *
 * This model:
 * 1. Trains on historical HR + validated energy data using auto-fit optimization
 * 2. Predicts using HRV-modified drain with anchoring to validated energy points
 * 3. Uses the last validated energy as starting point
 */
object HeartRatePredictionModel_modell2 {

    private const val TAG = "HRPredictionModel2"

    // Trained model parameters
    private var trainedParams: OptimizationResult? = null
    private var decayRate: DecayRateResult? = null
    private var sleepConfig = SleepConfig()
    private var energyConfig = EnergyConfig()

    // Last training info
    private var lastTrainingCycles = 0
    private var lastTrainingLoss = Double.POSITIVE_INFINITY

    /**
     * Trains the model parameters based on the provided data range.
     * Uses: Bereich=all, Methode=median, AutoFit=true, Offset=true
     */
    fun train(
        heartRateData: List<HeartRateEntry>,
        energyData: List<ValidatedEnergyLevelEntry>
    ) {
        if (heartRateData.isEmpty() || energyData.isEmpty()) {
            Log.w(TAG, "Insufficient data for training: HR=${heartRateData.size}, Energy=${energyData.size}")
            return
        }

        Log.d(TAG, "Training with ${heartRateData.size} HR points and ${energyData.size} energy points")

        // Convert to internal types
        val hrDataPoints = heartRateData.map { entry ->
            HRDataPoint(
                timestamp = entry.time,
                bpm = entry.bpm.toDouble()
            )
        }

        val energyDataPoints = energyData.map { entry ->
            EnergyDataPoint(
                timestamp = entry.time,
                percentage = entry.percentage.toDouble() * 100.0, // Convert 0-1 to 0-100
                validation = entry.validation.name
            )
        }

        // Aggregate HR data
        val hrAgg = EnergyCalculation.aggregateHR(hrDataPoints, energyConfig.aggregationMinutes)

        if (hrAgg.size < 10) {
            Log.w(TAG, "Not enough aggregated HR data: ${hrAgg.size}")
            return
        }

        // Run auto-fit optimization with best settings:
        // Bereich=ALL, Methode=MEDIAN, AutoFit=true (implicit), Offset=true (calculated in autoFit)
        val result = Optimizer.autoFit(
            hrAgg = hrAgg,
            validatedEnergy = energyDataPoints,
            sleepConfig = sleepConfig,
            aggregationMinutes = energyConfig.aggregationMinutes,
            fitRange = FitRange.ALL,
            aggregationMethod = AggregationMethod.MEDIAN
        )

        if (result.usedDays > 0) {
            trainedParams = result.result
            lastTrainingCycles = result.usedDays
            lastTrainingLoss = result.result.loss

            Log.i(TAG, "Training complete: ${result.usedDays}/${result.totalDays} cycles used")
            Log.i(TAG, "Parameters: hrLow=${result.result.hrLow}, hrHigh=${result.result.hrHigh}, " +
                    "drain=${result.result.drainFactor}, recovery=${result.result.recoveryFactor}, " +
                    "offset=${result.result.energyOffset}, loss=${result.result.loss}")

            // Compute personalized decay rate from validated energy history
            decayRate = EnergyDecayFallback.computeDecayRate(energyDataPoints)
        } else {
            Log.w(TAG, "Training failed: no valid cycles found (${result.totalDays} total)")
            // Use default parameters if training fails
            trainedParams = OptimizationResult(
                hrLow = 60.0,
                hrHigh = 100.0,
                drainFactor = 1.0,
                recoveryFactor = 1.0,
                loss = Double.POSITIVE_INFINITY,
                energyOffset = 0.0
            )
        }
    }

    /**
     * Predicts energy level for now and 2 hours into the future.
     * Uses: HRV=true, Anchor=true
     *
     * @param recentHeartRate HR data for the prediction window
     * @param validatedEnergy Validated energy points for anchoring
     * @return Pair of (currentEnergy, futureEnergy) as values between 0.0 and 1.0
     */
    fun predict(
        recentHeartRate: List<HeartRateEntry>,
        validatedEnergy: List<ValidatedEnergyLevelEntry> = emptyList()
    ): Pair<Double, Double> {
        val params = trainedParams ?: OptimizationResult(
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            loss = Double.POSITIVE_INFINITY,
            energyOffset = 0.0
        )

        if (recentHeartRate.isEmpty()) {
            Log.d(TAG, "No HR data for prediction, using personalized decay fallback")
            val lastValidated = validatedEnergy.maxByOrNull { it.time }
            val lastEnergy = lastValidated?.percentage?.toDouble() ?: 0.5
            val lastTime = lastValidated?.time ?: kotlinx.datetime.Clock.System.now()
            return EnergyDecayFallback.predictWithDecay(lastEnergy, lastTime, decayRate = decayRate)
        }

        // Convert to internal types
        val hrDataPoints = recentHeartRate.map { entry ->
            HRDataPoint(
                timestamp = entry.time,
                bpm = entry.bpm.toDouble()
            )
        }

        val energyDataPoints = validatedEnergy.map { entry ->
            EnergyDataPoint(
                timestamp = entry.time,
                percentage = entry.percentage.toDouble() * 100.0, // Convert 0-1 to 0-100
                validation = entry.validation.name
            )
        }

        // Aggregate HR data
        val hrAgg = EnergyCalculation.aggregateHR(hrDataPoints, energyConfig.aggregationMinutes)

        if (hrAgg.isEmpty()) {
            Log.d(TAG, "No aggregated HR data for prediction, using personalized decay fallback")
            val lastValidated = validatedEnergy.maxByOrNull { it.time }
            val lastEnergy = lastValidated?.percentage?.toDouble() ?: 0.5
            val lastTime = lastValidated?.time ?: kotlinx.datetime.Clock.System.now()
            return EnergyDecayFallback.predictWithDecay(lastEnergy, lastTime, decayRate = decayRate)
        }

        // Calculate HRV from HR data (HRV=true)
        val hrvData = HRVDrain.calculateHRVFromHR(hrDataPoints, windowMinutes = 5)

        // Detect wake events for potential reset
        val wakeEvents = SleepDetection.detectWakeEvents(hrAgg, sleepConfig)

        // Find last validated energy as fallback start energy
        val lastValidatedEnergy = energyDataPoints.maxByOrNull { it.timestamp }?.percentage ?: 50.0

        // Calculate energy using HRV-Drain with Anchoring (HRV=true, Anchor=true)
        val energyResults = HRVDrain.calculateEnergyWithHRVDrainAnchored(
            hrAgg = hrAgg,
            hrvData = hrvData,
            hrLow = params.hrLow,
            hrHigh = params.hrHigh,
            drainFactor = params.drainFactor,
            recoveryFactor = params.recoveryFactor,
            timeOffsetMinutes = energyConfig.timeOffsetMinutes,
            aggregationMinutes = energyConfig.aggregationMinutes,
            wakeEvents = wakeEvents,
            resetOnWake = sleepConfig.resetOnWake,
            validatedPoints = energyDataPoints,
            fallbackStartEnergy = lastValidatedEnergy,
            energyOffset = params.energyOffset,
            hrvConfig = HRVDrain.DEFAULT_HRV_DRAIN_CONFIG
        )

        val currentEnergy = if (energyResults.isNotEmpty()) {
            energyResults.last().energy / 100.0 // Convert back to 0-1
        } else {
            lastValidatedEnergy / 100.0
        }

        // For future prediction (2h), extrapolate based on recent trend
        val avgRecentHR = if (hrAgg.size >= 3) {
            hrAgg.takeLast(3).map { it.bpm }.average()
        } else {
            hrAgg.last().bpm
        }

        // Get average HRV multiplier from recent results
        val avgHRVMultiplier = if (energyResults.size >= 3) {
            energyResults.takeLast(3).map { it.hrvMultiplier }.average()
        } else if (energyResults.isNotEmpty()) {
            energyResults.last().hrvMultiplier
        } else {
            1.0
        }

        // Extrapolate 2 hours into future with HRV influence
        val futureEnergy = if (avgRecentHR > params.hrHigh) {
            // High HR drains energy (with HRV multiplier)
            val drainPer2h = (avgRecentHR - params.hrHigh) * 0.15 * params.drainFactor * avgHRVMultiplier * (120.0 / 15.0)
            ((currentEnergy * 100.0) - drainPer2h).coerceIn(0.0, 100.0) / 100.0
        } else if (avgRecentHR < params.hrLow) {
            // Low HR recovers energy (during sleep/rest)
            val recoveryPer2h = (params.hrLow - avgRecentHR) * 0.1 * params.recoveryFactor * (120.0 / 15.0)
            ((currentEnergy * 100.0) + recoveryPer2h).coerceIn(0.0, 100.0) / 100.0
        } else {
            // Neutral zone - slight decay
            currentEnergy * 0.95 + 0.5 * 0.05
        }

        Log.d(TAG, "Prediction: current=${"%.2f".format(currentEnergy)}, future=${"%.2f".format(futureEnergy)}, " +
                "avgHR=${"%.1f".format(avgRecentHR)}, hrvMult=${"%.2f".format(avgHRVMultiplier)}, " +
                "anchors=${energyDataPoints.size}")

        return Pair(currentEnergy.coerceIn(0.0, 1.0), futureEnergy.coerceIn(0.0, 1.0))
    }

    /**
     * Gets the current trained parameters (for debugging/display).
     */
    fun getTrainedParams(): OptimizationResult? = trainedParams

    /**
     * Gets training statistics.
     */
    fun getTrainingStats(): String {
        return "Cycles: $lastTrainingCycles, Loss: ${"%.2f".format(lastTrainingLoss)}"
    }

    /**
     * Updates the sleep configuration.
     */
    fun updateSleepConfig(config: SleepConfig) {
        sleepConfig = config
    }

    /**
     * Updates the energy configuration.
     */
    fun updateEnergyConfig(config: EnergyConfig) {
        energyConfig = config
    }
}
