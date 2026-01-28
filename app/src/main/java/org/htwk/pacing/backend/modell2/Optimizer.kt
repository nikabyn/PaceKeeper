package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant
import kotlin.math.sqrt
import kotlin.math.round
import android.util.Log

/**
 * Optimization algorithms
 * Includes grid search, Nelder-Mead optimization, and auto-fit functionality.
 */
object Optimizer {

    /**
     * Gets the last validated energy value before a given time.
     */
    private fun getLastValidationBefore(
        validatedEnergy: List<EnergyDataPoint>,
        before: Instant
    ): Double {
        val sorted = validatedEnergy
            .filter { it.timestamp < before }
            .sortedByDescending { it.timestamp }
        return sorted.firstOrNull()?.percentage ?: 50.0
    }

    /**
     * Groups data by sleep cycles for per-cycle optimization.
     */
    fun groupBySleepCycle(
        validatedEnergy: List<EnergyDataPoint>,
        hrAgg: List<HRDataPoint>,
        sleepCfg: SleepConfig
    ): List<CycleData> {
        val cycles = SleepDetection.getSleepCycles(hrAgg, sleepCfg)
        if (cycles.isEmpty()) return emptyList()

        val result = mutableListOf<CycleData>()

        for (cycle in cycles) {
            val cycleValidations = validatedEnergy.filter { v ->
                v.timestamp >= cycle.cycleStart && v.timestamp < cycle.cycleEnd
            }

            if (cycleValidations.size < 2) continue

            val cycleHR = hrAgg.filter { h ->
                h.timestamp >= cycle.cycleStart && h.timestamp < cycle.cycleEnd
            }

            if (cycleHR.isEmpty()) continue

            val startEnergy = getLastValidationBefore(validatedEnergy, cycle.cycleStart)

            result.add(
                CycleData(
                    label = cycle.label,
                    cycleStart = cycle.cycleStart,
                    cycleEnd = cycle.cycleEnd,
                    validatedPoints = cycleValidations.sortedBy { it.timestamp },
                    hrData = cycleHR,
                    startEnergy = startEnergy
                )
            )
        }

        return result
    }

    /**
     * Calculates the loss (MSE) for a given cycle with specific parameters.
     */
    fun calculateCycleLoss(
        cycle: CycleData,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        aggregationMinutes: Int
    ): Double {
        if (hrLow >= hrHigh) return Double.POSITIVE_INFINITY
        if (drainFactor <= 0 || recoveryFactor <= 0) return Double.POSITIVE_INFINITY

        val energyMap = EnergyCalculation.simulateEnergy(
            hrData = cycle.hrData,
            startEnergy = cycle.startEnergy,
            hrLow = hrLow,
            hrHigh = hrHigh,
            drainFactor = drainFactor,
            recoveryFactor = recoveryFactor,
            aggregationMinutes = aggregationMinutes
        )

        var sumSquaredError = 0.0
        var n = 0

        for (validated in cycle.validatedPoints) {
            val predicted = EnergyCalculation.findClosestEnergy(
                energyMap,
                validated.timestamp.toEpochMilliseconds()
            )

            if (predicted != null) {
                val error = predicted - validated.percentage
                sumSquaredError += error * error
                n++
            }
        }

        return if (n == 0) Double.POSITIVE_INFINITY else sumSquaredError / n
    }

    /**
     * Calculates the energy offset (median difference between predicted and validated).
     */
    fun calculateEnergyOffset(
        cycle: CycleData,
        hrLow: Double,
        hrHigh: Double,
        drainFactor: Double,
        recoveryFactor: Double,
        aggregationMinutes: Int
    ): Double {
        val energyMap = EnergyCalculation.simulateEnergy(
            hrData = cycle.hrData,
            startEnergy = cycle.startEnergy,
            hrLow = hrLow,
            hrHigh = hrHigh,
            drainFactor = drainFactor,
            recoveryFactor = recoveryFactor,
            aggregationMinutes = aggregationMinutes
        )

        val diffs = mutableListOf<Double>()

        for (validated in cycle.validatedPoints) {
            val predicted = EnergyCalculation.findClosestEnergy(
                energyMap,
                validated.timestamp.toEpochMilliseconds()
            )

            if (predicted != null) {
                diffs.add(predicted - validated.percentage)
            }
        }

        return if (diffs.isEmpty()) 0.0 else median(diffs)
    }

    /**
     * Grid search to find initial parameter estimates.
     */
    fun gridSearchCycle(
        cycle: CycleData,
        aggregationMinutes: Int
    ): OptimizationResult {
        val hrLowRange = listOf(50.0, 55.0, 60.0, 65.0, 70.0)
        val hrHighRange = listOf(75.0, 80.0, 90.0, 100.0, 110.0, 120.0, 130.0)
        val drainRange = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0)
        val recoveryRange = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0)

        return hrLowRange.flatMap { hrLow ->
            hrHighRange.flatMap { hrHigh ->
                drainRange.flatMap { drain ->
                    recoveryRange.map { recovery ->
                        OptimizationResult(
                            hrLow = hrLow,
                            hrHigh = hrHigh,
                            drainFactor = drain,
                            recoveryFactor = recovery,
                            loss = calculateCycleLoss(cycle, hrLow, hrHigh, drain, recovery, aggregationMinutes),
                            energyOffset = 0.0
                        )
                    }
                }
            }
        }
            .filter { it.hrLow < it.hrHigh }
            .minByOrNull { it.loss }
            ?: OptimizationResult(60.0, 100.0, 1.0, 1.0, Double.POSITIVE_INFINITY, 0.0)
    }

    /**
     * Nelder-Mead optimization for fine-tuning parameters.
     *
     */
    private data class SimplexPoint(val point: DoubleArray, val loss: Double)

    private fun calculateCentroid(simplex: List<SimplexPoint>): DoubleArray {
        val n = simplex[0].point.size
        val centroid = DoubleArray(n)
        for (i in 0 until simplex.lastIndex) {
            for (j in 0 until n) {
                centroid[j] += simplex[i].point[j]
            }
        }
        for (j in 0 until n) centroid[j] /= (simplex.size - 1).toDouble()
        return centroid
    }

    private fun reflect(centroid: DoubleArray, worst: DoubleArray, alpha: Double): DoubleArray =
        DoubleArray(centroid.size) { j -> centroid[j] + alpha * (centroid[j] - worst[j]) }

    private fun expand(centroid: DoubleArray, reflected: DoubleArray, gamma: Double): DoubleArray =
        DoubleArray(centroid.size) { j -> centroid[j] + gamma * (reflected[j] - centroid[j]) }

    private fun contract(centroid: DoubleArray, worst: DoubleArray, rho: Double): DoubleArray =
        DoubleArray(centroid.size) { j -> centroid[j] + rho * (worst[j] - centroid[j]) }

    private fun shrink(
        simplex: List<SimplexPoint>,
        best: DoubleArray,
        sigma: Double,
        getLoss: (DoubleArray) -> Double
    ): List<SimplexPoint> {
        return listOf(simplex[0]) + simplex.drop(1).map { sp ->
            val newPoint = DoubleArray(sp.point.size) { j ->
                best[j] + sigma * (sp.point[j] - best[j])
            }
            SimplexPoint(newPoint, getLoss(newPoint))
        }
    }

    private fun isConverged(simplex: List<SimplexPoint>, threshold: Double = 0.01): Boolean {
        val losses = simplex.mapNotNull { it.loss.takeIf { l -> l.isFinite() } }
        if (losses.isEmpty()) return false
        val mean = losses.average()
        val variance = losses.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance) < threshold
    }

    private fun updateSimplex(
        simplex: List<SimplexPoint>,
        getLoss: (DoubleArray) -> Double,
        alpha: Double,
        gamma: Double,
        rho: Double,
        sigma: Double
    ): List<SimplexPoint> {
        val best = simplex[0]
        val secondWorst = simplex[3]
        val worst = simplex[4]

        val centroid = calculateCentroid(simplex)
        val reflected = reflect(centroid, worst.point, alpha)
        val reflectedLoss = getLoss(reflected)

        return when {
            reflectedLoss < best.loss -> {
                val expanded = expand(centroid, reflected, gamma)
                val expandedLoss = getLoss(expanded)
                val replacement = if (expandedLoss < reflectedLoss)
                    SimplexPoint(expanded, expandedLoss)
                else
                    SimplexPoint(reflected, reflectedLoss)
                simplex.dropLast(1) + replacement
            }
            reflectedLoss < secondWorst.loss -> {
                simplex.dropLast(1) + SimplexPoint(reflected, reflectedLoss)
            }
            else -> {
                val contracted = contract(centroid, worst.point, rho)
                val contractedLoss = getLoss(contracted)
                if (contractedLoss < worst.loss) {
                    simplex.dropLast(1) + SimplexPoint(contracted, contractedLoss)
                } else {
                    shrink(simplex, best.point, sigma, getLoss)
                }
            }
        }
    }

    fun nelderMeadCycle(
        cycle: CycleData,
        startParams: OptimizationResult,
        aggregationMinutes: Int,
        maxIterations: Int = 50
    ): OptimizationResult {

        val getLoss: (DoubleArray) -> Double = { p ->
            calculateCycleLoss(cycle, p[0], p[1], p[2], p[3], aggregationMinutes)
        }

        val start = doubleArrayOf(
            startParams.hrLow,
            startParams.hrHigh,
            startParams.drainFactor,
            startParams.recoveryFactor
        )

        val initialSimplex = listOf(
            SimplexPoint(start.copyOf(), getLoss(start)),
            SimplexPoint(doubleArrayOf(start[0] + 3, start[1], start[2], start[3]), 0.0),
            SimplexPoint(doubleArrayOf(start[0], start[1] + 5, start[2], start[3]), 0.0),
            SimplexPoint(doubleArrayOf(start[0], start[1], start[2] + 0.3, start[3]), 0.0),
            SimplexPoint(doubleArrayOf(start[0], start[1], start[2], start[3] + 0.3), 0.0)
        ).mapIndexed { i, sp ->
            if (i == 0) sp else SimplexPoint(sp.point, getLoss(sp.point))
        }

        var simplex = initialSimplex

        for (iter in 0 until maxIterations) {
            simplex = simplex.sortedBy { it.loss }
            simplex = updateSimplex(simplex, getLoss, 1.0, 2.0, 0.5, 0.5)
            if (isConverged(simplex)) break
        }

        val best = simplex.minBy { it.loss }

        return OptimizationResult(
            hrLow          = round(best.point[0] * 10) / 10.0,
            hrHigh         = round(best.point[1] * 10) / 10.0,
            drainFactor    = round(best.point[2] * 100) / 100.0,
            recoveryFactor = round(best.point[3] * 100) / 100.0,
            loss           = best.loss,
            energyOffset   = 0.0
        )
    }

    /**
     * Fits a single sleep cycle.
     */
    fun fitSingleCycle(
        cycle: CycleData,
        aggregationMinutes: Int
    ): DayFitResult {
        Log.d("Optimizer fitSingleCycle", "Cycle ${cycle.label}: startEnergy=${cycle.startEnergy}, hrPoints=${cycle.hrData.size}, validatedPoints=${cycle.validatedPoints.size}")
        val gridResult = gridSearchCycle(cycle, aggregationMinutes)
        Log.d("Optimizer gridSearch", "Grid result: hrLow=${gridResult.hrLow}, hrHigh=${gridResult.hrHigh}, drain=${gridResult.drainFactor}, recovery=${gridResult.recoveryFactor}, loss=${gridResult.loss}")
        val finalResult = nelderMeadCycle(cycle, gridResult, aggregationMinutes)

        return DayFitResult(
            hrLow = finalResult.hrLow,
            hrHigh = finalResult.hrHigh,
            drainFactor = finalResult.drainFactor,
            recoveryFactor = finalResult.recoveryFactor,
            loss = finalResult.loss,
            energyOffset = finalResult.energyOffset,
            date = cycle.label,
            dataPoints = cycle.validatedPoints.size
        )
    }

    /**
     * Calculates the median of a list of values.
     */
    fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 != 0) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }

    /**
     * IQR-based aggregation (removes outliers).
     */
    fun iqrAggregate(values: List<Double>): Double {
        if (values.size < 4) return median(values)

        val sorted = values.sorted()
        val q1 = sorted[(sorted.size * 0.25).toInt()]
        val q3 = sorted[(sorted.size * 0.75).toInt()]
        val iqr = q3 - q1
        val lower = q1 - 1.5 * iqr
        val upper = q3 + 1.5 * iqr

        val filtered = values.filter { it >= lower && it <= upper }
        return if (filtered.isEmpty()) median(values) else filtered.average()
    }

    /**
     * Filters cycles by time range.
     */
    fun filterCyclesByRange(
        cycles: List<CycleData>,
        range: FitRange
    ): List<CycleData> {
        if (range == FitRange.ALL || cycles.isEmpty()) return cycles

        val sortedCycles = cycles.sortedByDescending { it.cycleStart }
        val lastDate = sortedCycles.first().cycleStart

        val cutoffDays = when (range) {
            FitRange.WEEK -> 7
            FitRange.MONTH -> 30
            else -> 0
        }

        val cutoffMs = lastDate.toEpochMilliseconds() - (cutoffDays * 24 * 60 * 60 * 1000L)
        val cutoff = Instant.fromEpochMilliseconds(cutoffMs)

        return cycles.filter { it.cycleStart >= cutoff }
    }

    /**
     * Main auto-fit function that optimizes parameters across all cycles.
     */
    fun autoFit(
        hrAgg: List<HRDataPoint>,
        validatedEnergy: List<EnergyDataPoint>,
        sleepConfig: SleepConfig,
        aggregationMinutes: Int,
        fitRange: FitRange = FitRange.ALL,
        aggregationMethod: AggregationMethod = AggregationMethod.MEDIAN
    ): AutoFitResult {
        val allCycles = groupBySleepCycle(validatedEnergy, hrAgg, sleepConfig)
        val cycles = filterCyclesByRange(allCycles, fitRange)
        val logTag = "Optimizer Autofit";
        Log.d(logTag, "Total cycles: ${allCycles.size}, Filtered cycles: ${cycles.size}, HR points: ${hrAgg.size}, Validated points: ${validatedEnergy.size}")

        if (cycles.isEmpty()) {
            return AutoFitResult(
                result = OptimizationResult(
                    hrLow = 60.0,
                    hrHigh = 100.0,
                    drainFactor = 1.0,
                    recoveryFactor = 1.0,
                    loss = Double.POSITIVE_INFINITY,
                    energyOffset = 0.0
                ),
                dayResults = emptyList(),
                usedDays = 0,
                totalDays = allCycles.size
            )
        }

        val dayResults = cycles.map { cycle ->
            fitSingleCycle(cycle, aggregationMinutes)
        }

        val validResults = dayResults.filter { it.loss < 500 && it.loss.isFinite() }

        if (validResults.isEmpty()) {
            return AutoFitResult(
                result = OptimizationResult(
                    hrLow = 60.0,
                    hrHigh = 100.0,
                    drainFactor = 1.0,
                    recoveryFactor = 1.0,
                    loss = Double.POSITIVE_INFINITY,
                    energyOffset = 0.0
                ),
                dayResults = dayResults,
                usedDays = 0,
                totalDays = allCycles.size
            )
        }

        val aggregate: (List<Double>) -> Double = when (aggregationMethod) {
            AggregationMethod.IQR -> ::iqrAggregate
            AggregationMethod.MEDIAN -> ::median
        }

        val aggHrLow = round(aggregate(validResults.map { it.hrLow }) * 10) / 10.0
        val aggHrHigh = round(aggregate(validResults.map { it.hrHigh }) * 10) / 10.0
        val aggDrainFactor = round(aggregate(validResults.map { it.drainFactor }) * 100) / 100.0
        val aggRecoveryFactor = round(aggregate(validResults.map { it.recoveryFactor }) * 100) / 100.0

        // Calculate energy offset
        val offsets = cycles.map { cycle ->
            calculateEnergyOffset(
                cycle,
                aggHrLow,
                aggHrHigh,
                aggDrainFactor,
                aggRecoveryFactor,
                aggregationMinutes
            )
        }

        val result = OptimizationResult(
            hrLow = aggHrLow,
            hrHigh = aggHrHigh,
            drainFactor = aggDrainFactor,
            recoveryFactor = aggRecoveryFactor,
            loss = aggregate(validResults.map { it.loss }),
            energyOffset = round(median(offsets) * 10) / 10.0
        )
        
        Log.d(logTag, "Final aggregated result: hrLow=${result.hrLow}, hrHigh=${result.hrHigh}, drain=${result.drainFactor}, recovery=${result.recoveryFactor}, offset=${result.energyOffset}, loss=${result.loss}")
        Log.d(logTag, "Valid results: ${validResults.size}, Day losses: ${validResults.map { it.loss }}")

        return AutoFitResult(
            result = result,
            dayResults = dayResults,
            usedDays = validResults.size,
            totalDays = allCycles.size
        )
    }
}
