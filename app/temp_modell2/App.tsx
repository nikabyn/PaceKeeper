import { useState, useEffect, useRef, useCallback, useMemo } from "react";
import { autoFitAnchored, FitRange2 } from "./optimizer2";
import {
  calculateHRVFromHR,
  calculateEnergyWithHRVDrain,
  calculateEnergyWithHRVDrainAnchored,
  HRVPoint,
  DEFAULT_HRV_DRAIN_CONFIG,
} from "./hrvDrain";
import Papa from "papaparse";
import {
  Chart,
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  TimeScale,
  Title,
  Tooltip,
  Legend,
  Filler,
} from "chart.js";
import "chartjs-adapter-date-fns";
import { HRDataPoint, EnergyDataPoint, Config } from "./types";
import {
  SleepConfig,
  FitRange,
  AggregationMethod,
  SleepCycle,
  detectWakeEvents,
  getSleepCycles,
  calculateEnergyWithParams,
  autoFit,
} from "./optimizer";
import {
  runFullEvaluation,
  formatEvaluationReport,
  EvaluationResult,
} from "./evaluation";
Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  TimeScale,
  Title,
  Tooltip,
  Legend,
  Filler,
);

const DEFAULT_CONFIG: Config = {
  hrLow: 60,
  hrHigh: 75,
  timeOffsetMinutes: 120,
  recoveryFactor: 8.6,
  drainFactor: 0.4,
  aggregationMinutes: 15,
  energyOffset: 0,
};

const DEFAULT_SLEEP_CONFIG: SleepConfig = {
  sleepHRThreshold: 62,
  wakeHRThreshold: 70,
  minSleepMinutes: 200,
  resetOnWake: false,
};

function App() {
  //für optimizer2
  const [fitRange2, setFitRange2] = useState<FitRange2>("all");
  const [fitting2, setFitting2] = useState(false);
  const [fitResult2, setFitResult2] = useState<string>("");
  const [useEnergyOffset, setUseEnergyOffset] = useState(true);
  const [hrData, setHrData] = useState<HRDataPoint[]>([]);
  const [energyData, setEnergyData] = useState<EnergyDataPoint[]>([]);
  const [config, setConfig] = useState<Config>(DEFAULT_CONFIG);
  const [sleepConfig, setSleepConfig] =
    useState<SleepConfig>(DEFAULT_SLEEP_CONFIG);
  const [interpolationOffset, setInterpolationOffset] = useState<number>(0);
  const [predictionOffset, setPredictionOffset] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [rangeMode, setRangeMode] = useState<"cycle" | "week" | "all">("all");
  const [selectedCycleIndex, setSelectedCycleIndex] = useState<number>(0);
  const [fitting, setFitting] = useState(false);
  const [fitResult, setFitResult] = useState<string>("");
  const [fitRange, setFitRange] = useState<FitRange>("all");
  const [aggMethod, setAggMethod] = useState<AggregationMethod>("median");
  const [startEnergyInfo, setStartEnergyInfo] = useState<string>("");
  const chartRef = useRef<HTMLCanvasElement>(null);
  const chartInstance = useRef<Chart | null>(null);
  const [anchorToValidated, setAnchorToValidated] = useState(false);
  const [evaluating, setEvaluating] = useState(false);
  const [evalProgress, setEvalProgress] = useState<string>("");
  const [evalResults, setEvalResults] = useState<EvaluationResult[] | null>(
    null,
  );
  const [hrvData, setHrvData] = useState<HRVPoint[]>([]);
  const [useHRVDrain, setUseHRVDrain] = useState(false);
  useEffect(() => {
    const loadData = async () => {
      try {
        const [hrRes, energyRes] = await Promise.all([
          fetch("/data/heart_rate.csv"),
          fetch("/data/validated_energy_level.csv"),
        ]);

        if (!hrRes.ok || !energyRes.ok)
          throw new Error("CSV-Dateien nicht gefunden");

        const hrText = await hrRes.text();
        const energyText = await energyRes.text();

        const hrParsed = Papa.parse(hrText, {
          header: true,
          skipEmptyLines: true,
        });
        const energyParsed = Papa.parse(energyText, {
          header: true,
          skipEmptyLines: true,
        });

        const hr: HRDataPoint[] = hrParsed.data
          .map((r: any) => ({
            timestamp: new Date(r.timestamp),
            bpm: parseFloat(r.bpm),
          }))
          .filter(
            (d: HRDataPoint) =>
              !isNaN(d.bpm) &&
              d.timestamp instanceof Date &&
              !isNaN(d.timestamp.getTime()),
          );

        const energy: EnergyDataPoint[] = energyParsed.data
          .map((r: any) => ({
            timestamp: new Date(r.timestamp),
            percentage: parseFloat(r.percentage),
            validation: r.validation,
          }))
          .filter(
            (d: EnergyDataPoint) =>
              !isNaN(d.percentage) &&
              d.timestamp instanceof Date &&
              !isNaN(d.timestamp.getTime()),
          );

        setHrData(hr);
        console.log("HR Datenpunkte:", hr.length);
        if (hr.length > 1) {
          const diffs = [];
          for (let i = 1; i < Math.min(100, hr.length); i++) {
            diffs.push(
              (hr[i].timestamp.getTime() - hr[i - 1].timestamp.getTime()) /
                1000,
            );
          }
          console.log(
            "HR Zeitabstand (Sek):",
            Math.min(...diffs),
            "-",
            Math.max(...diffs),
          );
        }
        setEnergyData(energy);
        // HRV aus HR-Daten berechnen
        const hrvMetrics = calculateHRVFromHR(hr, 60);
        setHrvData(hrvMetrics);
        setLoading(false);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Fehler beim Laden");
        setLoading(false);
      }
    };
    loadData();
  }, []);
  console.log("HRV Datenpunkte:", hrvData.length);
  if (hrvData.length > 0) {
    const rmssdValues = hrvData.map((h) => h.rmssd);
    console.log(
      "RMSSD min/max:",
      Math.min(...rmssdValues),
      Math.max(...rmssdValues),
    );
  }
  const aggregateHR = useCallback(
    (data: HRDataPoint[], minutes: number): HRDataPoint[] => {
      if (!data.length) return [];
      const buckets = new Map<number, number[]>();
      const ms = minutes * 60 * 1000;
      data.forEach((d) => {
        const key = Math.floor(d.timestamp.getTime() / ms) * ms;
        if (!buckets.has(key)) buckets.set(key, []);
        buckets.get(key)!.push(d.bpm);
      });
      return Array.from(buckets.entries())
        .map(([ts, vals]) => ({
          timestamp: new Date(ts),
          bpm: vals.reduce((a, b) => a + b, 0) / vals.length,
        }))
        .sort((a, b) => a.timestamp.getTime() - b.timestamp.getTime());
    },
    [],
  );

  const sleepCycles = useMemo(() => {
    if (!hrData.length) return [];
    const hrAgg = aggregateHR(hrData, config.aggregationMinutes);
    return getSleepCycles(hrAgg, sleepConfig).reverse(); // neueste zuerst
  }, [hrData, config.aggregationMinutes, sleepConfig, aggregateHR]);

  const availableWeeks = useMemo(() => {
    const weeks = new Set<string>();
    hrData.forEach((d) => {
      const date = new Date(d.timestamp);
      const day = date.getDay();
      const diff = date.getDate() - day + (day === 0 ? -6 : 1);
      const monday = new Date(date.setDate(diff));
      weeks.add(monday.toISOString().split("T")[0]);
    });
    return Array.from(weeks).sort().reverse();
  }, [hrData]);

  const getLastValidationBefore = useCallback(
    (before: Date): { energy: number; timestamp: Date } | null => {
      const sorted = energyData
        .filter((v) => v.timestamp < before)
        .sort((a, b) => b.timestamp.getTime() - a.timestamp.getTime());
      return sorted[0]
        ? { energy: sorted[0].percentage, timestamp: sorted[0].timestamp }
        : null;
    },
    [energyData],
  );

  const calculateEnergy = useCallback(
    (
      hrAgg: HRDataPoint[],
      cfg: Config,
      wakeEvents: { timestamp: Date }[],
      resetOnWake: boolean,
      offset: number,
      startEnergy: number,
    ): { timestamp: Date; energy: number }[] => {
      const result = calculateEnergyWithParams(
        hrAgg,
        cfg.hrLow,
        cfg.hrHigh,
        cfg.drainFactor,
        cfg.recoveryFactor,
        cfg.timeOffsetMinutes,
        cfg.aggregationMinutes,
        wakeEvents,
        resetOnWake,
        startEnergy,
        cfg.energyOffset,
      );
      return result.map((r) => ({
        ...r,
        energy: Math.max(0, Math.min(100, r.energy + offset)),
      }));
    },
    [],
  );
  const calculateEnergyAnchored = useCallback(
    (
      hrAgg: HRDataPoint[],
      cfg: Config,
      wakeEvents: { timestamp: Date }[],
      resetOnWake: boolean,
      offset: number,
      validatedPoints: EnergyDataPoint[],
      fallbackStartEnergy: number,
    ): { timestamp: Date; energy: number }[] => {
      if (hrAgg.length === 0) return [];

      const sortedValidated = [...validatedPoints].sort(
        (a, b) => a.timestamp.getTime() - b.timestamp.getTime(),
      );

      if (sortedValidated.length === 0) {
        return calculateEnergy(
          hrAgg,
          cfg,
          wakeEvents,
          resetOnWake,
          offset,
          fallbackStartEnergy,
        );
      }

      const result: { timestamp: Date; energy: number }[] = [];
      const offsetMs = cfg.timeOffsetMinutes * 60 * 1000;

      const hrStart = hrAgg[0].timestamp.getTime();
      const hrEnd = hrAgg[hrAgg.length - 1].timestamp.getTime();

      // Anchors mit zurückverschobenem Timestamp (für HR-Matching)
      const anchors: { timestamp: Date; energy: number }[] = [];

      // Ersten Anker finden (letzter validierter Punkt vor oder am HR-Start + offset)
      const beforeStart = sortedValidated.filter(
        (v) => v.timestamp.getTime() - offsetMs <= hrStart,
      );
      if (beforeStart.length > 0) {
        const last = beforeStart[beforeStart.length - 1];
        anchors.push({
          timestamp: hrAgg[0].timestamp,
          energy: last.percentage,
        });
      } else {
        anchors.push({
          timestamp: hrAgg[0].timestamp,
          energy: fallbackStartEnergy,
        });
      }

      // Alle validierten Punkte innerhalb des HR-Bereichs (mit Offset-Korrektur)
      sortedValidated.forEach((v) => {
        const adjustedTime = v.timestamp.getTime() - offsetMs;
        if (adjustedTime > hrStart && adjustedTime <= hrEnd) {
          anchors.push({
            timestamp: new Date(adjustedTime),
            energy: v.percentage,
          });
        }
      });

      // Für jedes Segment zwischen Ankern berechnen
      for (let i = 0; i < anchors.length; i++) {
        const anchor = anchors[i];
        const nextAnchor = anchors[i + 1];

        const segmentHR = hrAgg.filter((hr) => {
          const t = hr.timestamp.getTime();
          if (nextAnchor) {
            return (
              t >= anchor.timestamp.getTime() &&
              t < nextAnchor.timestamp.getTime()
            );
          } else {
            return t >= anchor.timestamp.getTime();
          }
        });

        if (segmentHR.length === 0) continue;

        const segmentWakeEvents = wakeEvents.filter((w) => {
          const t = w.timestamp.getTime();
          if (nextAnchor) {
            return (
              t >= anchor.timestamp.getTime() &&
              t < nextAnchor.timestamp.getTime()
            );
          } else {
            return t >= anchor.timestamp.getTime();
          }
        });

        const segmentResult = calculateEnergyWithParams(
          segmentHR,
          cfg.hrLow,
          cfg.hrHigh,
          cfg.drainFactor,
          cfg.recoveryFactor,
          cfg.timeOffsetMinutes,
          cfg.aggregationMinutes,
          segmentWakeEvents,
          resetOnWake,
          anchor.energy,
          cfg.energyOffset,
        );

        segmentResult.forEach((r) => {
          result.push({
            timestamp: r.timestamp,
            energy: Math.max(0, Math.min(100, r.energy + offset)),
          });
        });
      }

      return result;
    },
    [calculateEnergy],
  );
  const interpolateEnergy = useCallback(
    (
      validatedData: EnergyDataPoint[],
      hrAgg: HRDataPoint[],
      offset: number,
    ): { timestamp: Date; energy: number }[] => {
      if (validatedData.length < 2 || hrAgg.length === 0) return [];

      const sorted = [...validatedData].sort(
        (a, b) => a.timestamp.getTime() - b.timestamp.getTime(),
      );
      const result: { timestamp: Date; energy: number }[] = [];

      for (const hr of hrAgg) {
        const t = hr.timestamp.getTime();

        let before: EnergyDataPoint | null = null;
        let after: EnergyDataPoint | null = null;

        for (let i = 0; i < sorted.length; i++) {
          if (sorted[i].timestamp.getTime() <= t) {
            before = sorted[i];
          }
          if (sorted[i].timestamp.getTime() > t && !after) {
            after = sorted[i];
            break;
          }
        }

        let interpolatedValue: number;

        if (before && after) {
          const t0 = before.timestamp.getTime();
          const t1 = after.timestamp.getTime();
          const v0 = before.percentage;
          const v1 = after.percentage;
          const ratio = (t - t0) / (t1 - t0);
          interpolatedValue = v0 + ratio * (v1 - v0);
        } else if (before) {
          interpolatedValue = before.percentage;
        } else if (after) {
          interpolatedValue = after.percentage;
        } else {
          continue;
        }

        const finalValue = Math.max(
          0,
          Math.min(100, interpolatedValue + offset),
        );
        result.push({ timestamp: hr.timestamp, energy: finalValue });
      }

      return result;
    },
    [],
  );

  const runAutoFit = useCallback(() => {
    if (!hrData.length || !energyData.length) return;

    setFitting(true);
    setFitResult("Optimiere...");

    setTimeout(() => {
      const hrAgg = aggregateHR(hrData, config.aggregationMinutes);

      const { result, dayResults, usedDays, totalDays } = autoFit(
        hrAgg,
        energyData,
        sleepConfig,
        config.aggregationMinutes,
        fitRange,
        aggMethod,
      );

      if (usedDays === 0) {
        setFitResult(`Keine gültigen Zyklen gefunden (${totalDays} Segmente)`);
        setFitting(false);
        return;
      }

      setConfig((prev) => ({
        ...prev,
        hrLow: result.hrLow,
        hrHigh: result.hrHigh,
        drainFactor: result.drainFactor,
        recoveryFactor: result.recoveryFactor,
        energyOffset: result.energyOffset,
      }));

      const rangeText =
        fitRange === "all" ? "alle" : fitRange === "month" ? "Monat" : "Woche";
      const methodText = aggMethod === "median" ? "Median" : "IQR";
      setFitResult(
        `${usedDays}/${totalDays} Zyklen (${rangeText}, ${methodText}) | Loss=${result.loss.toFixed(1)} | ` +
          `hrLow=${result.hrLow} hrHigh=${result.hrHigh} drain=${result.drainFactor} recovery=${result.recoveryFactor} offset=${result.energyOffset}`,
      );
      setFitting(false);
    }, 50);
  }, [
    hrData,
    energyData,
    config,
    sleepConfig,
    fitRange,
    aggMethod,
    aggregateHR,
  ]);

  /*optimizer2*/
  const runAutoFitAnchored = useCallback(() => {
    if (!hrData.length || !energyData.length) return;

    setFitting2(true);
    setFitResult2("Optimiere (Anker)...");

    setTimeout(() => {
      const hrAgg = aggregateHR(hrData, config.aggregationMinutes);

      const result = autoFitAnchored(
        hrAgg,
        energyData,
        config.timeOffsetMinutes,
        fitRange2,
      );

      if (result.segmentsUsed === 0) {
        setFitResult2("Keine gültigen Segmente gefunden");
        setFitting2(false);
        return;
      }

      setConfig((prev) => ({
        ...prev,
        hrLow: result.hrLow,
        hrHigh: result.hrHigh,
        drainFactor: result.drainFactor,
        recoveryFactor: result.recoveryFactor,
      }));

      const rangeText =
        fitRange2 === "all"
          ? "alle"
          : fitRange2 === "month"
            ? "Monat"
            : "Woche";
      setFitResult2(
        `${result.segmentsUsed} Segmente (${rangeText}) | Loss=${result.loss.toFixed(1)} | ` +
          `hrLow=${result.hrLow} hrHigh=${result.hrHigh} drain=${result.drainFactor} recovery=${result.recoveryFactor}`,
      );
      setFitting2(false);
    }, 50);
  }, [
    hrData,
    energyData,
    config.aggregationMinutes,
    config.timeOffsetMinutes,
    fitRange2,
    aggregateHR,
  ]);
  const runEvaluation = useCallback(() => {
    if (!hrData.length || !energyData.length) return;

    setEvaluating(true);
    setEvalProgress("Starte Evaluation...");
    setEvalResults(null);

    setTimeout(() => {
      const results = runFullEvaluation(
        hrData,
        energyData,
        config,
        sleepConfig,
        (msg) => setEvalProgress(msg),
      );

      setEvalResults(results);
      setEvalProgress(`Fertig: ${results.length} Varianten getestet`);
      setEvaluating(false);

      console.log(formatEvaluationReport(results));
    }, 50);
  }, [hrData, energyData, config, sleepConfig]);

  const getTimeRange = useCallback((): { start: Date; end: Date } | null => {
    if (rangeMode === "all") return null;

    if (rangeMode === "cycle") {
      if (sleepCycles.length === 0 || selectedCycleIndex >= sleepCycles.length)
        return null;
      const cycle = sleepCycles[selectedCycleIndex];
      return { start: cycle.cycleStart, end: cycle.cycleEnd };
    } else {
      // week mode
      if (availableWeeks.length === 0) return null;
      const baseDate = new Date(availableWeeks[0]);
      const start = new Date(baseDate);
      start.setHours(0, 0, 0, 0);
      const end = new Date(baseDate);
      end.setDate(end.getDate() + 6);
      end.setHours(23, 59, 59, 999);
      return { start, end };
    }
  }, [rangeMode, sleepCycles, selectedCycleIndex, availableWeeks]);

  const filterByTimeRange = useCallback(
    <T extends { timestamp: Date }>(
      data: T[],
      range: { start: Date; end: Date } | null,
    ): T[] => {
      if (!range) return data;
      return data.filter(
        (d) => d.timestamp >= range.start && d.timestamp <= range.end,
      );
    },
    [],
  );

  useEffect(() => {
    if (loading || !chartRef.current || !hrData.length) return;

    const timeRange = getTimeRange();

    const hrAggAll = aggregateHR(hrData, config.aggregationMinutes);
    const wakeEvents = detectWakeEvents(hrAggAll, sleepConfig);

    const filteredRawHR = filterByTimeRange(hrData, timeRange);
    const hrAgg = aggregateHR(filteredRawHR, config.aggregationMinutes);

    // Startwert ermitteln
    let startEnergy = 50;
    let startInfo = "Fallback: 50%";

    if (
      rangeMode === "cycle" &&
      sleepCycles.length > 0 &&
      selectedCycleIndex < sleepCycles.length
    ) {
      const cycle = sleepCycles[selectedCycleIndex];
      const lastValidation = getLastValidationBefore(cycle.cycleStart);
      if (lastValidation) {
        startEnergy = lastValidation.energy;
        startInfo = `${lastValidation.energy.toFixed(1)}% (${lastValidation.timestamp.toLocaleString("de-DE")})`;
      }
    } else if (hrAgg.length > 0) {
      const firstHrTimestamp = hrAgg[0].timestamp;
      const lastValidation = getLastValidationBefore(firstHrTimestamp);
      if (lastValidation) {
        startEnergy = lastValidation.energy;
        startInfo = `${lastValidation.energy.toFixed(1)}% (${lastValidation.timestamp.toLocaleString("de-DE")})`;
      }
    }
    setStartEnergyInfo(startInfo);

    const filteredWakeEvents = filterByTimeRange(wakeEvents, timeRange);
    const filteredValidated = filterByTimeRange(energyData, timeRange);
    const effectiveConfig = {
      ...config,
      energyOffset: useEnergyOffset ? config.energyOffset : 0,
    };

    const predicted = useHRVDrain
      ? anchorToValidated
        ? calculateEnergyWithHRVDrainAnchored(
            hrAgg,
            filterByTimeRange(hrvData, timeRange),
            effectiveConfig.hrLow,
            effectiveConfig.hrHigh,
            effectiveConfig.drainFactor,
            effectiveConfig.recoveryFactor,
            effectiveConfig.timeOffsetMinutes,
            effectiveConfig.aggregationMinutes,
            filteredWakeEvents,
            sleepConfig.resetOnWake,
            filteredValidated,
            startEnergy,
            effectiveConfig.energyOffset,
          ).map((p) => ({ timestamp: p.timestamp, energy: p.energy }))
        : calculateEnergyWithHRVDrain(
            hrAgg,
            filterByTimeRange(hrvData, timeRange),
            effectiveConfig.hrLow,
            effectiveConfig.hrHigh,
            effectiveConfig.drainFactor,
            effectiveConfig.recoveryFactor,
            effectiveConfig.timeOffsetMinutes,
            effectiveConfig.aggregationMinutes,
            filteredWakeEvents,
            sleepConfig.resetOnWake,
            startEnergy,
            effectiveConfig.energyOffset,
          ).map((p) => ({ timestamp: p.timestamp, energy: p.energy }))
      : anchorToValidated
        ? calculateEnergyAnchored(
            hrAgg,
            effectiveConfig,
            filteredWakeEvents,
            sleepConfig.resetOnWake,
            predictionOffset,
            filteredValidated,
            startEnergy,
          )
        : calculateEnergy(
            hrAgg,
            effectiveConfig,
            filteredWakeEvents,
            sleepConfig.resetOnWake,
            predictionOffset,
            startEnergy,
          );
    console.log("HRV aktiv:", useHRVDrain, "HRV-Daten:", hrvData.length);
    const interpolated = interpolateEnergy(
      filteredValidated,
      hrAgg,
      interpolationOffset,
    );
    if (chartInstance.current) chartInstance.current.destroy();

    const hrLow = config.hrLow;
    const hrHigh = config.hrHigh;

    chartInstance.current = new Chart(chartRef.current, {
      type: "line",
      data: {
        datasets: [
          {
            label: "Herzrate (BPM)",
            data: hrAgg.map((d) => ({ x: d.timestamp.getTime(), y: d.bpm })),
            borderColor: "#888",
            backgroundColor: "rgba(136, 136, 136, 0.1)",
            borderWidth: 2,
            pointRadius: 0,
            yAxisID: "yHR",
            tension: 0,
            segment: {
              borderColor: (ctx: any) => {
                if (!ctx.p0 || !ctx.p1) return "#888";
                const y0 = ctx.p0.parsed.y;
                const y1 = ctx.p1.parsed.y;
                const avg = (y0 + y1) / 2;
                if (avg > hrHigh) return "#f44336";
                if (avg < hrLow) return "#4caf50";
                return "#888";
              },
            },
          },
          {
            label: "Energie (Prognose)",
            data: predicted.map((d) => ({
              x: d.timestamp.getTime(),
              y: d.energy,
            })),
            borderColor: "#4fc3f7",
            borderWidth: 2,
            //borderDash: [5, 5],
            pointRadius: 0,
            yAxisID: "yEnergy",
            tension: 0, //Kurvenglättung
          },
          {
            label: "Energie (Interpoliert)",
            data: interpolated.map((d) => ({
              x: d.timestamp.getTime(),
              y: d.energy,
            })),
            borderColor: "#ff9800",
            borderWidth: 2,
            pointRadius: 0,
            yAxisID: "yEnergy",
            tension: 0,
          },
          {
            label: "Energie (Validiert)",
            data: filteredValidated.map((d) => ({
              x: d.timestamp.getTime(),
              y: d.percentage,
            })),
            borderColor: "#4caf50",
            backgroundColor: "#4caf50",
            borderWidth: 0,
            pointRadius: 5,
            pointStyle: "circle",
            showLine: false,
            yAxisID: "yEnergy",
          },
          {
            label: "Aufwachen",
            data: filteredWakeEvents.map((w) => ({
              x: w.timestamp.getTime(),
              y: 100,
            })),
            borderColor: "#ffeb3b",
            backgroundColor: "#ffeb3b",
            borderWidth: 0,
            pointRadius: 8,
            pointStyle: "triangle",
            showLine: false,
            yAxisID: "yEnergy",
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        interaction: { mode: "index", intersect: false },
        plugins: {
          legend: { labels: { color: "#eee" } },
          tooltip: {
            callbacks: {
              title: (items) =>
                new Date(items[0].parsed.x).toLocaleString("de-DE"),
            },
          },
        },
        scales: {
          x: {
            type: "time",
            time: { displayFormats: { hour: "dd.MM HH:mm", day: "dd.MM" } },
            ticks: { color: "#888" },
            grid: { color: "#333" },
          },
          yHR: {
            type: "linear",
            position: "left",
            title: { display: true, text: "BPM", color: "#888" },
            ticks: { color: "#888" },
            grid: { color: "#333" },
            min: 40,
            max: 150,
          },
          yEnergy: {
            type: "linear",
            position: "right",
            title: { display: true, text: "Energie %", color: "#4fc3f7" },
            ticks: { color: "#4fc3f7" },
            grid: { drawOnChartArea: false },
            min: 0,
            max: 100,
          },
        },
      },
    });

    return () => {
      chartInstance.current?.destroy();
    };
  }, [
    hrData,
    energyData,
    config,
    sleepConfig,
    interpolationOffset,
    predictionOffset,
    rangeMode,
    selectedCycleIndex,
    sleepCycles,
    loading,
    aggregateHR,
    calculateEnergy,
    calculateEnergyAnchored,
    anchorToValidated,
    filterByTimeRange,
    getTimeRange,
    interpolateEnergy,
    getLastValidationBefore,
    useEnergyOffset,
    useHRVDrain,
    hrvData,
  ]);

  const updateConfig = (key: keyof Config, value: number) => {
    setConfig((prev) => ({ ...prev, [key]: value }));
  };

  const updateSleepConfig = (
    key: keyof SleepConfig,
    value: number | boolean,
  ) => {
    setSleepConfig((prev) => ({ ...prev, [key]: value }));
  };

  const handleRangeModeChange = (mode: "cycle" | "week" | "all") => {
    setRangeMode(mode);
    if (mode === "cycle" && sleepCycles.length) {
      setSelectedCycleIndex(0);
    }
  };

  if (loading)
    return (
      <div className="app">
        <div className="loading">Lade Daten...</div>
      </div>
    );
  if (error)
    return (
      <div className="app">
        <div className="error">{error}</div>
      </div>
    );

  return (
    <div className="app">
      <h1>Energie & Herzrate</h1>
      <div className="controls">
        <div className="control-group">
          <label>HR Low</label>
          <input
            type="number"
            value={config.hrLow}
            onChange={(e) => updateConfig("hrLow", +e.target.value)}
          />
        </div>
        <div className="control-group">
          <label>HR High</label>
          <input
            type="number"
            value={config.hrHigh}
            onChange={(e) => updateConfig("hrHigh", +e.target.value)}
          />
        </div>
        <div className="control-group">
          <label>Offset (min)</label>
          <input
            type="number"
            value={config.timeOffsetMinutes}
            onChange={(e) => updateConfig("timeOffsetMinutes", +e.target.value)}
          />
        </div>
        <div className="control-group">
          <label>Recovery</label>
          <input
            type="number"
            step="0.1"
            value={config.recoveryFactor}
            onChange={(e) => updateConfig("recoveryFactor", +e.target.value)}
          />
        </div>
        <div className="control-group">
          <label>Drain</label>
          <input
            type="number"
            step="0.1"
            value={config.drainFactor}
            onChange={(e) => updateConfig("drainFactor", +e.target.value)}
          />
        </div>
        <div className="control-group">
          <label>HRV-Drain</label>
          <input
            type="checkbox"
            checked={useHRVDrain}
            onChange={(e) => setUseHRVDrain(e.target.checked)}
          />
        </div>
        <div className="control-group">
          <label>Aggregation (min)</label>
          <input
            type="number"
            value={config.aggregationMinutes}
            onChange={(e) =>
              updateConfig("aggregationMinutes", +e.target.value)
            }
          />
        </div>
        <div className="control-group">
          <label>Schlaf HR ≤</label>
          <input
            type="number"
            value={sleepConfig.sleepHRThreshold}
            onChange={(e) =>
              updateSleepConfig("sleepHRThreshold", +e.target.value)
            }
          />
        </div>
        <div className="control-group">
          <label>Wach HR ≥</label>
          <input
            type="number"
            value={sleepConfig.wakeHRThreshold}
            onChange={(e) =>
              updateSleepConfig("wakeHRThreshold", +e.target.value)
            }
          />
        </div>
        <div className="control-group">
          <label>Min Schlaf (min)</label>
          <input
            type="number"
            value={sleepConfig.minSleepMinutes}
            onChange={(e) =>
              updateSleepConfig("minSleepMinutes", +e.target.value)
            }
          />
        </div>
        <div className="control-group">
          <label>Reset 100%</label>
          <input
            type="checkbox"
            checked={sleepConfig.resetOnWake}
            onChange={(e) => updateSleepConfig("resetOnWake", e.target.checked)}
          />
        </div>
        <div className="control-group">
          <label>Prognose Offset</label>
          <input
            type="number"
            step="1"
            value={predictionOffset}
            onChange={(e) => setPredictionOffset(+e.target.value)}
          />
        </div>
        <div className="control-group">
          <label>Interpol. Offset</label>
          <input
            type="number"
            step="1"
            value={interpolationOffset}
            onChange={(e) => setInterpolationOffset(+e.target.value)}
          />
        </div>
        <div className="control-group">
          <label>Energy Offset</label>
          <input
            type="number"
            step="1"
            value={config.energyOffset}
            onChange={(e) => updateConfig("energyOffset", +e.target.value)}
          />
          <input
            type="checkbox"
            checked={useEnergyOffset}
            onChange={(e) => setUseEnergyOffset(e.target.checked)}
            title="Energy Offset aktivieren"
          />
        </div>
        <div className="control-group">
          <label>Zeitraum</label>
          <select
            value={rangeMode}
            onChange={(e) =>
              handleRangeModeChange(e.target.value as "cycle" | "week" | "all")
            }
          >
            <option value="cycle">Schlafzyklus</option>
            <option value="week">Woche</option>
            <option value="all">Alle</option>
          </select>
        </div>
        {rangeMode === "cycle" && sleepCycles.length > 0 && (
          <div className="control-group">
            <label>Zyklus wählen</label>
            <select
              value={selectedCycleIndex}
              onChange={(e) => setSelectedCycleIndex(+e.target.value)}
            >
              {sleepCycles.map((cycle, idx) => (
                <option key={idx} value={idx}>
                  {cycle.cycleStart.toLocaleString("de-DE", {
                    weekday: "short",
                    day: "2-digit",
                    month: "2-digit",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}{" "}
                  →{" "}
                  {cycle.cycleEnd.toLocaleString("de-DE", {
                    day: "2-digit",
                    month: "2-digit",
                    hour: "2-digit",
                    minute: "2-digit",
                  })}
                </option>
              ))}
            </select>
          </div>
        )}
        {rangeMode === "week" && (
          <div className="control-group">
            <label>Woche wählen</label>
            <select>
              {availableWeeks.map((d) => {
                const start = new Date(d);
                const end = new Date(d);
                end.setDate(end.getDate() + 6);
                return (
                  <option key={d} value={d}>
                    {start.toLocaleDateString("de-DE", {
                      day: "2-digit",
                      month: "2-digit",
                    })}{" "}
                    -{" "}
                    {end.toLocaleDateString("de-DE", {
                      day: "2-digit",
                      month: "2-digit",
                      year: "numeric",
                    })}
                  </option>
                );
              })}
            </select>
          </div>
        )}
      </div>
      <div className="controls fit-controls">
        <div className="control-group">
          <label>Fit-Bereich</label>
          <select
            value={fitRange}
            onChange={(e) => setFitRange(e.target.value as FitRange)}
          >
            <option value="all">Alle Daten</option>
            <option value="month">Letzter Monat</option>
            <option value="week">Letzte Woche</option>
          </select>
        </div>
        <div className="control-group">
          <label>Aggregation</label>
          <select
            value={aggMethod}
            onChange={(e) => setAggMethod(e.target.value as AggregationMethod)}
          >
            <option value="median">Median</option>
            <option value="iqr">IQR (Ausreißer entfernt)</option>
          </select>
        </div>
        <div className="control-group">
          <label>&nbsp;</label>
          <button
            onClick={runAutoFit}
            disabled={fitting}
            className="fit-button"
          >
            {fitting ? "Läuft..." : "Auto-Fit"}
          </button>
        </div>
        <div className="control-group">
          <label>Anker an Validiert</label>
          <input
            type="checkbox"
            checked={anchorToValidated}
            onChange={(e) => setAnchorToValidated(e.target.checked)}
          />
        </div>
      </div>

      {fitResult && <div className="fit-result">{fitResult}</div>}
      <div className="controls fit-controls">
        <div className="control-group">
          <label>Fit-Bereich (Anker)</label>
          <select
            value={fitRange2}
            onChange={(e) => setFitRange2(e.target.value as FitRange2)}
          >
            <option value="all">Alle Daten</option>
            <option value="month">Letzter Monat</option>
            <option value="week">Letzte Woche</option>
          </select>
        </div>
        <div className="control-group">
          <label>&nbsp;</label>
          <button
            onClick={runAutoFitAnchored}
            disabled={fitting2}
            className="fit-button"
          >
            {fitting2 ? "Läuft..." : "Auto-Fit (Anker)"}
          </button>
        </div>
      </div>
      {fitResult2 && <div className="fit-result">{fitResult2}</div>}

      <div className="controls fit-controls">
        <div className="control-group">
          <label>&nbsp;</label>
          <button
            onClick={runEvaluation}
            disabled={evaluating}
            className="fit-button"
          >
            {evaluating ? "Läuft..." : "Evaluation starten"}
          </button>
        </div>
        {evalProgress && (
          <div className="control-group">
            <label>&nbsp;</label>
            <span>{evalProgress}</span>
          </div>
        )}
      </div>
      {evalResults && evalResults.length > 0 && (
        <div className="eval-result">
          <strong>
            Beste Variante (RMSE={evalResults[0].metrics.rmse.toFixed(2)}):
          </strong>{" "}
          {evalResults[0].config.fitRange}, {evalResults[0].config.aggMethod},{" "}
          AutoFit={String(evalResults[0].config.useAutoFit)}, Offset=
          {String(evalResults[0].config.useEnergyOffset)}
          {" | "}Params: hrLow={evalResults[0].params.hrLow}, hrHigh=
          {evalResults[0].params.hrHigh}, drain=
          {evalResults[0].params.drainFactor}, recovery=
          {evalResults[0].params.recoveryFactor}
        </div>
      )}
      {rangeMode === "cycle" && startEnergyInfo && (
        <div className="start-energy-info">Startwert: {startEnergyInfo}</div>
      )}
      <div className="chart-container">
        <canvas ref={chartRef}></canvas>
      </div>
    </div>
  );
}

export default App;
