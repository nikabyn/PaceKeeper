import { HRDataPoint, EnergyDataPoint, Config } from "./types";

export interface SleepPhase {
  start: Date;
  end: Date;
}

export interface WakeEvent {
  timestamp: Date;
}

export interface SleepConfig {
  sleepHRThreshold: number;
  wakeHRThreshold: number;
  minSleepMinutes: number;
  resetOnWake: boolean;
}

export interface OptimizationResult {
  hrLow: number;
  hrHigh: number;
  drainFactor: number;
  recoveryFactor: number;
  loss: number;
  energyOffset: number;
}

export interface DayFitResult extends OptimizationResult {
  date: string;
  dataPoints: number;
}

export type FitRange = "all" | "month" | "week";
export type AggregationMethod = "median" | "iqr";

const HR_DELAY_MS = 2 * 60 * 60 * 1000;

export function detectSleepPhases(
  hrAgg: HRDataPoint[],
  sleepCfg: SleepConfig,
): SleepPhase[] {
  if (hrAgg.length < 2) return [];

  const phases: SleepPhase[] = [];
  let inSleep = false;
  let sleepStart: Date | null = null;

  for (let i = 0; i < hrAgg.length; i++) {
    const hr = hrAgg[i].bpm;
    const ts = hrAgg[i].timestamp;

    if (!inSleep && hr < sleepCfg.sleepHRThreshold) {
      // Zurückschauen: wann hat HR angefangen zu sinken?
      let peakIdx = i;
      for (let j = i - 1; j >= 0 && j >= i - 20; j--) {
        if (hrAgg[j].bpm > hrAgg[peakIdx].bpm) {
          peakIdx = j;
        }
      }
      inSleep = true;
      sleepStart = hrAgg[peakIdx].timestamp;
    }

    if (inSleep && hr >= sleepCfg.wakeHRThreshold) {
      const sleepDuration = sleepStart
        ? (ts.getTime() - sleepStart.getTime()) / 60000
        : 0;
      if (sleepDuration >= sleepCfg.minSleepMinutes) {
        phases.push({ start: sleepStart!, end: ts });
      }
      inSleep = false;
      sleepStart = null;
    }
  }

  return phases;
}

export function detectWakeEvents(
  hrAgg: HRDataPoint[],
  sleepCfg: SleepConfig,
): WakeEvent[] {
  const phases = detectSleepPhases(hrAgg, sleepCfg);
  return phases.map((p) => ({ timestamp: p.end }));
}

export interface SleepCycle {
  cycleStart: Date;
  cycleEnd: Date;
  label: string;
}

export function getSleepCycles(
  hrAgg: HRDataPoint[],
  sleepCfg: SleepConfig,
): SleepCycle[] {
  const phases = detectSleepPhases(hrAgg, sleepCfg);
  if (phases.length < 2) return [];

  const cycles: SleepCycle[] = [];
  for (let i = 0; i < phases.length - 1; i++) {
    cycles.push({
      cycleStart: phases[i].start,
      cycleEnd: phases[i + 1].start,
      label: phases[i].start.toISOString().split("T")[0],
    });
  }
  return cycles;
}

export function calculateEnergyWithParams(
  hrAgg: HRDataPoint[],
  hrLow: number,
  hrHigh: number,
  drainFactor: number,
  recoveryFactor: number,
  timeOffsetMinutes: number,
  aggregationMinutes: number,
  wakeEvents: WakeEvent[],
  resetOnWake: boolean,
  startEnergy: number,
  energyOffset: number = 0,
): { timestamp: Date; energy: number }[] {
  if (!hrAgg.length) return [];
  const result: { timestamp: Date; energy: number }[] = [];
  let energy = startEnergy;
  const offsetMs = timeOffsetMinutes * 60 * 1000;

  const wakeTimestamps = new Set(wakeEvents.map((w) => w.timestamp.getTime()));

  for (let i = 0; i < hrAgg.length; i++) {
    const hr = hrAgg[i].bpm;
    const ts = hrAgg[i].timestamp;
    const deltaMinutes =
      i > 0
        ? (ts.getTime() - hrAgg[i - 1].timestamp.getTime()) / 60000
        : aggregationMinutes;

    if (resetOnWake && wakeTimestamps.has(ts.getTime())) {
      energy = 100;
    } else {
      if (hr < hrLow) {
        energy += (hrLow - hr) * 0.1 * recoveryFactor * (deltaMinutes / 15);
      } else if (hr > hrHigh) {
        energy -= (hr - hrHigh) * 0.15 * drainFactor * (deltaMinutes / 15);
      }
      energy = Math.max(0, Math.min(100, energy));
    }

    result.push({
      timestamp: new Date(ts.getTime() + offsetMs),
      energy: Math.max(0, Math.min(100, energy - energyOffset)),
    });
  }
  return result;
}

interface CycleData {
  label: string;
  cycleStart: Date;
  cycleEnd: Date;
  validatedPoints: EnergyDataPoint[];
  hrData: HRDataPoint[];
  startEnergy: number;
}

function getLastValidationBefore(
  validatedEnergy: EnergyDataPoint[],
  before: Date,
): number {
  const sorted = validatedEnergy
    .filter((v) => v.timestamp < before)
    .sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
  return sorted[0] ? sorted[0].percentage : 50;
}

function groupBySleepCycle(
  validatedEnergy: EnergyDataPoint[],
  hrAgg: HRDataPoint[],
  sleepCfg: SleepConfig,
): CycleData[] {
  const cycles = getSleepCycles(hrAgg, sleepCfg);
  if (cycles.length === 0) return [];

  const result: CycleData[] = [];

  for (const cycle of cycles) {
    const cycleValidations = validatedEnergy.filter(
      (v) => v.timestamp >= cycle.cycleStart && v.timestamp < cycle.cycleEnd,
    );

    if (cycleValidations.length < 2) continue;

    const cycleHR = hrAgg.filter(
      (h) => h.timestamp >= cycle.cycleStart && h.timestamp < cycle.cycleEnd,
    );

    if (cycleHR.length === 0) continue;

    const startEnergy = getLastValidationBefore(
      validatedEnergy,
      cycle.cycleStart,
    );

    result.push({
      label: cycle.label,
      cycleStart: cycle.cycleStart,
      cycleEnd: cycle.cycleEnd,
      validatedPoints: cycleValidations.sort(
        (a, b) => a.timestamp.getTime() - b.timestamp.getTime(),
      ),
      hrData: cycleHR,
      startEnergy,
    });
  }

  return result;
}

function simulateEnergy(
  hrData: HRDataPoint[],
  startEnergy: number,
  hrLow: number,
  hrHigh: number,
  drainFactor: number,
  recoveryFactor: number,
  aggregationMinutes: number,
): Map<number, number> {
  const result = new Map<number, number>();

  let energy = startEnergy;

  for (let i = 0; i < hrData.length; i++) {
    const hr = hrData[i].bpm;
    const ts = hrData[i].timestamp.getTime();
    const deltaMinutes =
      i > 0
        ? (ts - hrData[i - 1].timestamp.getTime()) / 60000
        : aggregationMinutes;

    if (hr < hrLow) {
      energy += (hrLow - hr) * 0.1 * recoveryFactor * (deltaMinutes / 15);
    } else if (hr > hrHigh) {
      energy -= (hr - hrHigh) * 0.15 * drainFactor * (deltaMinutes / 15);
    }
    energy = Math.max(0, Math.min(100, energy));

    result.set(ts + HR_DELAY_MS, energy);
  }

  return result;
}

function findClosestEnergy(
  energyMap: Map<number, number>,
  targetTime: number,
): number | null {
  let closest: number | null = null;
  let minDiff = Infinity;

  for (const [ts, energy] of energyMap) {
    const diff = Math.abs(ts - targetTime);
    if (diff < minDiff) {
      minDiff = diff;
      closest = energy;
    }
  }

  if (minDiff > 30 * 60 * 1000) return null;
  return closest;
}

function calculateCycleLoss(
  cycle: CycleData,
  hrLow: number,
  hrHigh: number,
  drainFactor: number,
  recoveryFactor: number,
  aggregationMinutes: number,
): number {
  if (hrLow >= hrHigh) return Infinity;
  if (drainFactor <= 0 || recoveryFactor <= 0) return Infinity;

  const energyMap = simulateEnergy(
    cycle.hrData,
    cycle.startEnergy,
    hrLow,
    hrHigh,
    drainFactor,
    recoveryFactor,
    aggregationMinutes,
  );

  let sumSquaredError = 0;
  let n = 0;

  for (const validated of cycle.validatedPoints) {
    const predicted = findClosestEnergy(
      energyMap,
      validated.timestamp.getTime(),
    );

    if (predicted !== null) {
      const error = predicted - validated.percentage;
      sumSquaredError += error * error;
      n++;
    }
  }

  if (n === 0) return Infinity;
  return sumSquaredError / n;
}

function calculateEnergyOffset(
  cycle: CycleData,
  hrLow: number,
  hrHigh: number,
  drainFactor: number,
  recoveryFactor: number,
  aggregationMinutes: number,
): number {
  const energyMap = simulateEnergy(
    cycle.hrData,
    cycle.startEnergy,
    hrLow,
    hrHigh,
    drainFactor,
    recoveryFactor,
    aggregationMinutes,
  );

  const diffs: number[] = [];

  for (const validated of cycle.validatedPoints) {
    const predicted = findClosestEnergy(
      energyMap,
      validated.timestamp.getTime(),
    );

    if (predicted !== null) {
      diffs.push(predicted - validated.percentage);
    }
  }

  if (diffs.length === 0) return 0;
  const sorted = [...diffs].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 !== 0
    ? sorted[mid]
    : (sorted[mid - 1] + sorted[mid]) / 2;
}

function gridSearchCycle(
  cycle: CycleData,
  aggregationMinutes: number,
): OptimizationResult {
  const hrLowRange = [50, 55, 60, 65, 70, 75];
  const hrHighRange = [80, 90, 100, 110, 120, 130];
  const drainRange = [0.5, 1.0, 1.5, 2.0, 2.5, 3.0];
  const recoveryRange = [0.5, 1.0, 1.5, 2.0, 2.5, 3.0];

  let bestLoss = Infinity;
  let bestParams: OptimizationResult = {
    hrLow: 60,
    hrHigh: 100,
    drainFactor: 1.0,
    recoveryFactor: 1.0,
    loss: Infinity,
    energyOffset: 0,
  };

  for (const hrLow of hrLowRange) {
    for (const hrHigh of hrHighRange) {
      if (hrLow >= hrHigh) continue;
      for (const drainFactor of drainRange) {
        for (const recoveryFactor of recoveryRange) {
          const loss = calculateCycleLoss(
            cycle,
            hrLow,
            hrHigh,
            drainFactor,
            recoveryFactor,
            aggregationMinutes,
          );

          if (loss < bestLoss) {
            bestLoss = loss;
            bestParams = {
              hrLow,
              hrHigh,
              drainFactor,
              recoveryFactor,
              loss,
              energyOffset: 0,
            };
          }
        }
      }
    }
  }

  return bestParams;
}

function nelderMeadCycle(
  cycle: CycleData,
  startParams: OptimizationResult,
  aggregationMinutes: number,
  maxIterations: number = 50,
): OptimizationResult {
  type Point = [number, number, number, number];

  const getLoss = (p: Point): number => {
    return calculateCycleLoss(
      cycle,
      p[0],
      p[1],
      p[2],
      p[3],
      aggregationMinutes,
    );
  };

  const start: Point = [
    startParams.hrLow,
    startParams.hrHigh,
    startParams.drainFactor,
    startParams.recoveryFactor,
  ];

  let simplex: { point: Point; loss: number }[] = [
    { point: [...start] as Point, loss: getLoss(start) },
    { point: [start[0] + 3, start[1], start[2], start[3]] as Point, loss: 0 },
    { point: [start[0], start[1] + 5, start[2], start[3]] as Point, loss: 0 },
    { point: [start[0], start[1], start[2] + 0.3, start[3]] as Point, loss: 0 },
    { point: [start[0], start[1], start[2], start[3] + 0.3] as Point, loss: 0 },
  ];

  for (let i = 1; i < simplex.length; i++) {
    simplex[i].loss = getLoss(simplex[i].point);
  }

  const alpha = 1.0,
    gamma = 2.0,
    rho = 0.5,
    sigma = 0.5;

  for (let iter = 0; iter < maxIterations; iter++) {
    simplex.sort((a, b) => a.loss - b.loss);

    const best = simplex[0];
    const secondWorst = simplex[3];
    const worst = simplex[4];

    const centroid: Point = [0, 0, 0, 0];
    for (let i = 0; i < 4; i++) {
      for (let j = 0; j < 4; j++) {
        centroid[j] += simplex[i].point[j];
      }
    }
    for (let j = 0; j < 4; j++) centroid[j] /= 4;

    const reflected: Point = [0, 0, 0, 0] as Point;
    for (let j = 0; j < 4; j++) {
      reflected[j] = centroid[j] + alpha * (centroid[j] - worst.point[j]);
    }
    const reflectedLoss = getLoss(reflected);

    if (reflectedLoss < best.loss) {
      const expanded: Point = [0, 0, 0, 0] as Point;
      for (let j = 0; j < 4; j++) {
        expanded[j] = centroid[j] + gamma * (reflected[j] - centroid[j]);
      }
      const expandedLoss = getLoss(expanded);
      simplex[4] =
        expandedLoss < reflectedLoss
          ? { point: expanded, loss: expandedLoss }
          : { point: reflected, loss: reflectedLoss };
    } else if (reflectedLoss < secondWorst.loss) {
      simplex[4] = { point: reflected, loss: reflectedLoss };
    } else {
      const contracted: Point = [0, 0, 0, 0] as Point;
      for (let j = 0; j < 4; j++) {
        contracted[j] = centroid[j] + rho * (worst.point[j] - centroid[j]);
      }
      const contractedLoss = getLoss(contracted);

      if (contractedLoss < worst.loss) {
        simplex[4] = { point: contracted, loss: contractedLoss };
      } else {
        for (let i = 1; i < 5; i++) {
          for (let j = 0; j < 4; j++) {
            simplex[i].point[j] =
              best.point[j] + sigma * (simplex[i].point[j] - best.point[j]);
          }
          simplex[i].loss = getLoss(simplex[i].point);
        }
      }
    }

    const losses = simplex.map((s) => s.loss).filter((l) => isFinite(l));
    if (losses.length > 0) {
      const mean = losses.reduce((a, b) => a + b, 0) / losses.length;
      const variance =
        losses.reduce((a, b) => a + (b - mean) ** 2, 0) / losses.length;
      if (Math.sqrt(variance) < 0.01) break;
    }
  }

  simplex.sort((a, b) => a.loss - b.loss);
  const best = simplex[0];

  return {
    hrLow: Math.round(best.point[0] * 10) / 10,
    hrHigh: Math.round(best.point[1] * 10) / 10,
    drainFactor: Math.round(best.point[2] * 100) / 100,
    recoveryFactor: Math.round(best.point[3] * 100) / 100,
    loss: best.loss,
    energyOffset: 0,
  };
}

function fitSingleCycle(
  cycle: CycleData,
  aggregationMinutes: number,
): DayFitResult {
  const gridResult = gridSearchCycle(cycle, aggregationMinutes);
  const finalResult = nelderMeadCycle(cycle, gridResult, aggregationMinutes);

  return {
    ...finalResult,
    date: cycle.label,
    dataPoints: cycle.validatedPoints.length,
  };
}

function median(values: number[]): number {
  if (values.length === 0) return 0;
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  return sorted.length % 2 !== 0
    ? sorted[mid]
    : (sorted[mid - 1] + sorted[mid]) / 2;
}

function iqrAggregate(values: number[]): number {
  if (values.length < 4) return median(values);

  const sorted = [...values].sort((a, b) => a - b);
  const q1 = sorted[Math.floor(sorted.length * 0.25)];
  const q3 = sorted[Math.floor(sorted.length * 0.75)];
  const iqr = q3 - q1;
  const lower = q1 - 1.5 * iqr;
  const upper = q3 + 1.5 * iqr;

  const filtered = values.filter((v) => v >= lower && v <= upper);
  if (filtered.length === 0) return median(values);

  return filtered.reduce((a, b) => a + b, 0) / filtered.length;
}

function filterCyclesByRange(
  cycles: CycleData[],
  range: FitRange,
): CycleData[] {
  if (range === "all" || cycles.length === 0) return cycles;

  const sortedCycles = [...cycles].sort(
    (a, b) => b.cycleStart.getTime() - a.cycleStart.getTime(),
  );
  const lastDate = sortedCycles[0].cycleStart;

  const cutoffDays = range === "week" ? 7 : 30;
  const cutoff = new Date(lastDate);
  cutoff.setDate(cutoff.getDate() - cutoffDays);

  return cycles.filter((c) => c.cycleStart >= cutoff);
}

export function autoFit(
  hrAgg: HRDataPoint[],
  validatedEnergy: EnergyDataPoint[],
  sleepConfig: SleepConfig,
  aggregationMinutes: number,
  fitRange: FitRange = "all",
  aggregationMethod: AggregationMethod = "median",
): {
  result: OptimizationResult;
  dayResults: DayFitResult[];
  usedDays: number;
  totalDays: number;
} {
  const allCycles = groupBySleepCycle(validatedEnergy, hrAgg, sleepConfig);
  const cycles = filterCyclesByRange(allCycles, fitRange);

  if (cycles.length === 0) {
    return {
      result: {
        hrLow: 60,
        hrHigh: 100,
        drainFactor: 1.0,
        recoveryFactor: 1.0,
        loss: Infinity,
        energyOffset: 0,
      },
      dayResults: [],
      usedDays: 0,
      totalDays: allCycles.length,
    };
  }

  const dayResults: DayFitResult[] = cycles.map((cycle) =>
    fitSingleCycle(cycle, aggregationMinutes),
  );
  const validResults = dayResults.filter(
    (r) => r.loss < 500 && isFinite(r.loss),
  );

  if (validResults.length === 0) {
    return {
      result: {
        hrLow: 60,
        hrHigh: 100,
        drainFactor: 1.0,
        recoveryFactor: 1.0,
        loss: Infinity,
        energyOffset: 0,
      },
      dayResults,
      usedDays: 0,
      totalDays: allCycles.length,
    };
  }

  const aggregate = aggregationMethod === "iqr" ? iqrAggregate : median;

  const aggHrLow =
    Math.round(aggregate(validResults.map((r) => r.hrLow)) * 10) / 10;
  const aggHrHigh =
    Math.round(aggregate(validResults.map((r) => r.hrHigh)) * 10) / 10;
  const aggDrainFactor =
    Math.round(aggregate(validResults.map((r) => r.drainFactor)) * 100) / 100;
  const aggRecoveryFactor =
    Math.round(aggregate(validResults.map((r) => r.recoveryFactor)) * 100) /
    100;

  const offsets: number[] = [];
  for (const cycle of cycles) {
    const offset = calculateEnergyOffset(
      cycle,
      aggHrLow,
      aggHrHigh,
      aggDrainFactor,
      aggRecoveryFactor,
      aggregationMinutes,
    );
    offsets.push(offset);
  }

  const result: OptimizationResult = {
    hrLow: aggHrLow,
    hrHigh: aggHrHigh,
    drainFactor: aggDrainFactor,
    recoveryFactor: aggRecoveryFactor,
    loss: aggregate(validResults.map((r) => r.loss)),
    energyOffset: Math.round(median(offsets) * 10) / 10,
  };

  return {
    result,
    dayResults,
    usedDays: validResults.length,
    totalDays: allCycles.length,
  };
}
