import { HRDataPoint } from "./types";

export interface HRVPoint {
  timestamp: Date;
  rmssd: number;
}

export interface HRVDrainConfig {
  windowSeconds: number;
  baselineWindowHours: number;
  lowHRVMultiplier: number;
  normalHRVMultiplier: number;
  highHRVMultiplier: number;
  lowThreshold: number;
  highThreshold: number;
}

export const DEFAULT_HRV_DRAIN_CONFIG: HRVDrainConfig = {
  windowSeconds: 60,
  baselineWindowHours: 24,
  lowHRVMultiplier: 1.5,
  normalHRVMultiplier: 1.0,
  highHRVMultiplier: 0.5,
  lowThreshold: 0.7,
  highThreshold: 1.3,
};

function calculateRMSSD(rrIntervals: number[]): number {
  if (rrIntervals.length < 2) return 0;

  let sumSquaredDiffs = 0;
  for (let i = 1; i < rrIntervals.length; i++) {
    const diff = rrIntervals[i] - rrIntervals[i - 1];
    sumSquaredDiffs += diff * diff;
  }

  return Math.sqrt(sumSquaredDiffs / (rrIntervals.length - 1));
}

export function calculateHRVFromHR(
  hrData: HRDataPoint[],
  windowMinutes: number = 5,
): HRVPoint[] {
  if (hrData.length < 5) return [];

  const sorted = [...hrData].sort(
    (a, b) => a.timestamp.getTime() - b.timestamp.getTime(),
  );

  const results: HRVPoint[] = [];
  const windowMs = windowMinutes * 60 * 1000;

  for (let i = 0; i < sorted.length; i++) {
    const windowEnd = sorted[i].timestamp.getTime();
    const windowStart = windowEnd - windowMs;

    const windowData = sorted.filter(
      (d) =>
        d.timestamp.getTime() > windowStart &&
        d.timestamp.getTime() <= windowEnd,
    );

    if (windowData.length < 3) continue;

    // RMSSD aus HR-Differenzen (nicht RR-Intervallen)
    const hrValues = windowData.map((d) => d.bpm);
    let sumSquaredDiffs = 0;
    for (let j = 1; j < hrValues.length; j++) {
      const diff = hrValues[j] - hrValues[j - 1];
      sumSquaredDiffs += diff * diff;
    }
    const rmssd = Math.sqrt(sumSquaredDiffs / (hrValues.length - 1));

    if (rmssd >= 0 && rmssd < 50) {
      results.push({
        timestamp: sorted[i].timestamp,
        rmssd,
      });
    }
  }

  return results;
}

export function calculateHRVBaseline(hrvData: HRVPoint[]): number {
  if (hrvData.length === 0) return 50;

  const values = hrvData.map((d) => d.rmssd).sort((a, b) => a - b);
  const mid = Math.floor(values.length / 2);

  return values.length % 2 === 0
    ? (values[mid - 1] + values[mid]) / 2
    : values[mid];
}

function getHRVAtTime(
  hrvData: HRVPoint[],
  targetTime: Date,
  maxDiffMs: number = 5 * 60 * 1000,
): number | null {
  if (hrvData.length === 0) return null;

  const t = targetTime.getTime();
  let closest = hrvData[0];
  let minDiff = Math.abs(hrvData[0].timestamp.getTime() - t);

  for (const point of hrvData) {
    const diff = Math.abs(point.timestamp.getTime() - t);
    if (diff < minDiff) {
      minDiff = diff;
      closest = point;
    }
  }

  return minDiff <= maxDiffMs ? closest.rmssd : null;
}

function getDrainMultiplier(
  currentHRV: number | null,
  baseline: number,
  config: HRVDrainConfig,
): number {
  if (currentHRV === null) return config.normalHRVMultiplier;

  const ratio = currentHRV / baseline;

  if (ratio < config.lowThreshold) {
    return config.lowHRVMultiplier;
  } else if (ratio > config.highThreshold) {
    return config.highHRVMultiplier;
  }
  return config.normalHRVMultiplier;
}

export function calculateEnergyWithHRVDrain(
  hrAgg: HRDataPoint[],
  hrvData: HRVPoint[],
  hrLow: number,
  hrHigh: number,
  drainFactor: number,
  recoveryFactor: number,
  timeOffsetMinutes: number,
  aggregationMinutes: number,
  wakeEvents: { timestamp: Date }[],
  resetOnWake: boolean,
  startEnergy: number,
  energyOffset: number,
  hrvConfig: HRVDrainConfig = DEFAULT_HRV_DRAIN_CONFIG,
): { timestamp: Date; energy: number; hrvMultiplier: number }[] {
  if (!hrAgg.length) return [];

  const result: { timestamp: Date; energy: number; hrvMultiplier: number }[] =
    [];
  let energy = startEnergy;
  const offsetMs = timeOffsetMinutes * 60 * 1000;
  const wakeTimestamps = new Set(wakeEvents.map((w) => w.timestamp.getTime()));

  const baseline = calculateHRVBaseline(hrvData);

  for (let i = 0; i < hrAgg.length; i++) {
    const hr = hrAgg[i].bpm;
    const ts = hrAgg[i].timestamp;
    const deltaMinutes =
      i > 0
        ? (ts.getTime() - hrAgg[i - 1].timestamp.getTime()) / 60000
        : aggregationMinutes;

    const currentHRV = getHRVAtTime(hrvData, ts);
    const hrvMultiplier = getDrainMultiplier(currentHRV, baseline, hrvConfig);

    if (resetOnWake && wakeTimestamps.has(ts.getTime())) {
      energy = 100;
    } else {
      if (hr < hrLow) {
        energy += (hrLow - hr) * 0.1 * recoveryFactor * (deltaMinutes / 15);
      } else if (hr > hrHigh) {
        energy -=
          (hr - hrHigh) *
          0.15 *
          drainFactor *
          hrvMultiplier *
          (deltaMinutes / 15);
      }
      energy = Math.max(0, Math.min(100, energy));
    }

    result.push({
      timestamp: new Date(ts.getTime() + offsetMs),
      energy: Math.max(0, Math.min(100, energy - energyOffset)),
      hrvMultiplier,
    });
  }

  return result;
}

export function calculateEnergyWithHRVDrainAnchored(
  hrAgg: HRDataPoint[],
  hrvData: HRVPoint[],
  hrLow: number,
  hrHigh: number,
  drainFactor: number,
  recoveryFactor: number,
  timeOffsetMinutes: number,
  aggregationMinutes: number,
  wakeEvents: { timestamp: Date }[],
  resetOnWake: boolean,
  validatedPoints: { timestamp: Date; percentage: number }[],
  fallbackStartEnergy: number,
  energyOffset: number,
  hrvConfig: HRVDrainConfig = DEFAULT_HRV_DRAIN_CONFIG,
): { timestamp: Date; energy: number; hrvMultiplier: number }[] {
  if (hrAgg.length === 0) return [];

  const sortedValidated = [...validatedPoints].sort(
    (a, b) => a.timestamp.getTime() - b.timestamp.getTime(),
  );

  const offsetMs = timeOffsetMinutes * 60 * 1000;
  const hrStart = hrAgg[0].timestamp.getTime();
  const hrEnd = hrAgg[hrAgg.length - 1].timestamp.getTime();

  const anchors: { timestamp: Date; energy: number }[] = [];

  const beforeStart = sortedValidated.filter(
    (v) => v.timestamp.getTime() - offsetMs <= hrStart,
  );
  if (beforeStart.length > 0) {
    anchors.push({
      timestamp: hrAgg[0].timestamp,
      energy: beforeStart[beforeStart.length - 1].percentage,
    });
  } else {
    anchors.push({
      timestamp: hrAgg[0].timestamp,
      energy: fallbackStartEnergy,
    });
  }

  sortedValidated.forEach((v) => {
    const adjustedTime = v.timestamp.getTime() - offsetMs;
    if (adjustedTime > hrStart && adjustedTime <= hrEnd) {
      anchors.push({
        timestamp: new Date(adjustedTime),
        energy: v.percentage,
      });
    }
  });

  const result: { timestamp: Date; energy: number; hrvMultiplier: number }[] =
    [];
  const baseline = calculateHRVBaseline(hrvData);

  for (let i = 0; i < anchors.length; i++) {
    const anchor = anchors[i];
    const nextAnchor = anchors[i + 1];

    const segmentHR = hrAgg.filter((hr) => {
      const t = hr.timestamp.getTime();
      return nextAnchor
        ? t >= anchor.timestamp.getTime() && t < nextAnchor.timestamp.getTime()
        : t >= anchor.timestamp.getTime();
    });

    if (segmentHR.length === 0) continue;

    const segmentHRV = hrvData.filter((h) => {
      const t = h.timestamp.getTime();
      return nextAnchor
        ? t >= anchor.timestamp.getTime() && t < nextAnchor.timestamp.getTime()
        : t >= anchor.timestamp.getTime();
    });

    const segmentWakeEvents = wakeEvents.filter((w) => {
      const t = w.timestamp.getTime();
      return nextAnchor
        ? t >= anchor.timestamp.getTime() && t < nextAnchor.timestamp.getTime()
        : t >= anchor.timestamp.getTime();
    });

    const segmentResult = calculateEnergyWithHRVDrain(
      segmentHR,
      segmentHRV,
      hrLow,
      hrHigh,
      drainFactor,
      recoveryFactor,
      timeOffsetMinutes,
      aggregationMinutes,
      segmentWakeEvents,
      resetOnWake,
      anchor.energy,
      energyOffset,
      hrvConfig,
    );

    result.push(...segmentResult);
  }

  return result;
}
