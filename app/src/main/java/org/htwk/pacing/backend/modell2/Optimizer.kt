package org.htwk.pacing.backend.modell2

import kotlinx.datetime.Instant
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Optimization algorithms translated from TypeScript optimizer.ts
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
        val hrLowRange = listOf(50.0, 55.0, 60.0, 65.0, 70.0, 75.0)
        val hrHighRange = listOf(80.0, 90.0, 100.0, 110.0, 120.0, 130.0)
        val drainRange = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0)
        val recoveryRange = listOf(0.5, 1.0, 1.5, 2.0, 2.5, 3.0)

        var bestLoss = Double.POSITIVE_INFINITY
        var bestParams = OptimizationResult(
            hrLow = 60.0,
            hrHigh = 100.0,
            drainFactor = 1.0,
            recoveryFactor = 1.0,
            loss = Double.POSITIVE_INFINITY,
            energyOffset = 0.0
        )

        for (hrLow in hrLowRange) {
            for (hrHigh in hrHighRange) {
                if (hrLow >= hrHigh) continue
                for (drainFactor in drainRange) {
                    for (recoveryFactor in recoveryRange) {
                        val loss = calculateCycleLoss(
                            cycle,
                            hrLow,
                            hrHigh,
                            drainFactor,
                            recoveryFactor,
                            aggregationMinutes
                        )

                        if (loss < bestLoss) {
                            bestLoss = loss
                            bestParams = OptimizationResult(
                                hrLow = hrLow,
                                hrHigh = hrHigh,
                                drainFactor = drainFactor,
                                recoveryFactor = recoveryFactor,
                                loss = loss,
                                energyOffset = 0.0
                            )
                        }
                    }
                }
            }
        }

        return bestParams
    }

    /**
     * Nelder-Mead optimization for fine-tuning parameters.
     */
    fun nelderMeadCycle(
        cycle: CycleData,
        startParams: OptimizationResult,
        aggregationMinutes: Int,
        maxIterations: Int = 50
    ): OptimizationResult {
        // Point is [hrLow, hrHigh, drainFactor, recoveryFactor]
        fun getLoss(p: DoubleArray): Double {
            return calculateCycleLoss(
                cycle,
                p[0],
                p[1],
                p[2],
                p[3],
                aggregationMinutes
            )
        }

        val start = doubleArrayOf(
            startParams.hrLow,
            startParams.hrHigh,
            startParams.drainFactor,
            startParams.recoveryFactor
        )

        // Initialize simplex
        data class SimplexPoint(val point: DoubleArray, var loss: Double)

        val simplex = mutableListOf(
            SimplexPoint(start.copyOf(), getLoss(start)),
            SimplexPoint(doubleArrayOf(start[0] + 3, start[1], start[2], start[3]), 0.0),
            SimplexPoint(doubleArrayOf(start[0], start[1] + 5, start[2], start[3]), 0.0),
            SimplexPoint(doubleArrayOf(start[0], start[1], start[2] + 0.3, start[3]), 0.0),
            SimplexPoint(doubleArrayOf(start[0], start[1], start[2], start[3] + 0.3), 0.0)
        )

        for (i in 1 until simplex.size) {
            simplex[i].loss = getLoss(simplex[i].point)
        }

        val alpha = 1.0
        val gamma = 2.0
        val rho = 0.5
        val sigma = 0.5

        for (iter in 0 until maxIterations) {
            simplex.sortBy { it.loss }

            val best = simplex[0]
            val secondWorst = simplex[3]
            val worst = simplex[4]

            // Calculate centroid (excluding worst point)
            val centroid = DoubleArray(4)
            for (i in 0 until 4) {
                for (j in 0 until 4) {
                    centroid[j] += simplex[i].point[j]
                }
            }
            for (j in 0 until 4) centroid[j] /= 4.0

            // Reflection
            val reflected = DoubleArray(4) { j ->
                centroid[j] + alpha * (centroid[j] - worst.point[j])
            }
            val reflectedLoss = getLoss(reflected)

            if (reflectedLoss < best.loss) {
                // Expansion
                val expanded = DoubleArray(4) { j ->
                    centroid[j] + gamma * (reflected[j] - centroid[j])
                }
                val expandedLoss = getLoss(expanded)

                if (expandedLoss < reflectedLoss) {
                    simplex[4] = SimplexPoint(expanded, expandedLoss)
                } else {
                    simplex[4] = SimplexPoint(reflected, reflectedLoss)
                }
            } else if (reflectedLoss < secondWorst.loss) {
                simplex[4] = SimplexPoint(reflected, reflectedLoss)
            } else {
                // Contraction
                val contracted = DoubleArray(4) { j ->
                    centroid[j] + rho * (worst.point[j] - centroid[j])
                }
                val contractedLoss = getLoss(contracted)

                if (contractedLoss < worst.loss) {
                    simplex[4] = SimplexPoint(contracted, contractedLoss)
                } else {
                    // Shrink
                    for (i in 1 until 5) {
                        for (j in 0 until 4) {
                            simplex[i].point[j] = best.point[j] + sigma * (simplex[i].point[j] - best.point[j])
                        }
                        simplex[i].loss = getLoss(simplex[i].point)
                    }
                }
            }

            // Check convergence
            val losses = simplex.map { it.loss }.filter { it.isFinite() }
            if (losses.isNotEmpty()) {
                val mean = losses.average()
                val variance = losses.map { (it - mean) * (it - mean) }.average()
                if (sqrt(variance) < 0.01) break
            }
        }

        simplex.sortBy { it.loss }
        val best = simplex[0]

        return OptimizationResult(
            hrLow = (best.point[0] * 10).toLong() / 10.0,
            hrHigh = (best.point[1] * 10).toLong() / 10.0,
            drainFactor = (best.point[2] * 100).toLong() / 100.0,
            recoveryFactor = (best.point[3] * 100).toLong() / 100.0,
            loss = best.loss,
            energyOffset = 0.0
        )
    }

    /**
     * Fits a single sleep cycle.
     */
    fun fitSingleCycle(
        cycle: CycleData,
        aggregationMinutes: Int
    ): DayFitResult {
        val gridResult = gridSearchCycle(cycle, aggregationMinutes)
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

        val aggHrLow = (aggregate(validResults.map { it.hrLow }) * 10).toLong() / 10.0
        val aggHrHigh = (aggregate(validResults.map { it.hrHigh }) * 10).toLong() / 10.0
        val aggDrainFactor = (aggregate(validResults.map { it.drainFactor }) * 100).toLong() / 100.0
        val aggRecoveryFactor = (aggregate(validResults.map { it.recoveryFactor }) * 100).toLong() / 100.0

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
            energyOffset = (median(offsets) * 10).toLong() / 10.0
        )

        return AutoFitResult(
            result = result,
            dayResults = dayResults,
            usedDays = validResults.size,
            totalDays = allCycles.size
        )
    }
}
