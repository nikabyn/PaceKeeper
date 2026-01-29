# Ausführliche Analyse: Model2 - Energielevel-Prognose

## Übersicht

Das **Model2** ist ein System zur Vorhersage des Energielevels einer Person basierend auf Herzfrequenzdaten (HR) und Herzratenvariabilität (HRV). Es wurde für eine Pacing-App entwickelt, vermutlich für Menschen mit chronischer Erschöpfung (z.B. ME/CFS).

---

## Architektur-Diagramm (Datenfluss)

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           DATEN-EINGABE                                          │
├─────────────────────────────────────────────────────────────────────────────────┤
│  HeartRateEntry[]     ValidatedEnergyLevelEntry[]                               │
│  (Herzfrequenz)       (Nutzer-validierte Energie)                               │
└─────────────┬─────────────────────────┬─────────────────────────────────────────┘
              │                         │
              ▼                         ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        PredictorModel2 (Fassade)                                │
│                        ========================                                 │
│  • train(heartRate, energy)     → Trainiert das Modell                         │
│  • predict(input)               → Generiert Vorhersage                          │
│  • aggregateHR(data, minutes)   → Aggregiert HR-Daten                           │
└───────┬────────────────────────────────────────────────────────────────┬────────┘
        │                                                                │
        │ TRAINING                                                       │ PREDICTION
        ▼                                                                ▼
┌───────────────────────────────────────┐    ┌────────────────────────────────────┐
│         SleepDetection                │    │           HRVDrain                 │
│         ==============                │    │           ========                 │
│ • detectSleepPhases(hrAgg, config)    │    │ • calculateHRVFromHR(hrData)       │
│ • getSleepCycles(hrAgg, config)       │    │ • calculateHRVBaseline(hrvData)    │
│ • findPeakTimestamp(hrAgg, idx)       │    │ • getDrainMultiplier(hrv, base)    │
└───────────────┬───────────────────────┘    └──────────────┬─────────────────────┘
                │                                           │
                ▼                                           │
┌───────────────────────────────────────┐                   │
│            Optimizer                  │                   │
│            =========                  │                   │
│ • groupBySleepCycle()                 │                   │
│ • gridSearchCycle()                   │                   │
│ • nelderMeadCycle()                   │                   │
│ • autoFit()                           │◄──────────────────┘
│ • simulateEnergy()                    │
│ • calculateCycleLoss()                │
└───────────────┬───────────────────────┘
                │
                ▼ Optimierte Parameter
┌───────────────────────────────────────────────────────────────────────────────────┐
│                        EnergyCalculation                                          │
│                        =================                                          │
│ • calculateEnergyWithHRVDrainAnchored() ← HAUPT-ENTRY-POINT                      │
│ • calculateEnergyWithHRVDrain()                                                   │
│ • calculateEnergyChange()                                                         │
│ • buildAnchorPoints()                                                             │
└───────────────────────────────────────────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                        EnergyDecayFallback                                        │
│                        ==================                                         │
│ • computeDecayRate(energyData)         (wenn keine HR-Daten)                     │
│ • predictWithDecay(lastEnergy, time)                                             │
│ • getDecayForHour(rate, hour)                                                    │
└───────────────────────────────────────────────────────────────────────────────────┘
                │
                ▼
┌───────────────────────────────────────────────────────────────────────────────────┐
│                           AUSGABE                                                 │
│  PredictedEnergyLevelEntryModel2                                                 │
│  • time: Instant                                                                  │
│  • percentageNow: Percentage (0-1)                                               │
│  • timeFuture: Instant (+2h)                                                     │
│  • percentageFuture: Percentage (0-1)                                            │
└───────────────────────────────────────────────────────────────────────────────────┘
```

---

## Detaillierte Beschreibung der Dateien und Funktionen

### 1. Types.kt

**Pfad:** `app/src/main/java/org/htwk/pacing/backend/model2/Types.kt`

**Zweck:** Definiert alle Datenklassen (DTOs) für das gesamte Modul.

| Datenklasse | Beschreibung |
|-------------|--------------|
| `HRDataPoint` | Einzelner Herzfrequenz-Messpunkt (timestamp, bpm) |
| `EnergyDataPoint` | Energie-Datenpunkt mit optionaler Validierung |
| `SleepPhase` | Schlafphase mit Start- und Endzeit |
| `WakeEvent` | Aufwach-Ereignis (Ende einer Schlafphase) |
| `SleepConfig` | Konfiguration für Schlaferkennung (Schwellwerte) |
| `SleepCycle` | Ein vollständiger Schlafzyklus (Tag) |
| `OptimizationResult` | Ergebnis der Parameteroptimierung |
| `DayFitResult` | Fit-Ergebnis für einen einzelnen Tag |
| `EnergyConfig` | Globale Energie-Konfiguration |
| `HRVPoint` | HRV-Messpunkt (timestamp, RMSSD) |
| `HRVDrainConfig` | HRV-Multiplikator-Konfiguration |
| `CycleData` | Daten für einen Schlafzyklus |
| `EnergyResult`/`EnergyResultWithHRV` | Berechnete Energie mit Zeitstempel |
| `DecayRateResult` | Abklingrate nach Tageszeit |

---

### 2. PredictorModel2.kt

**Pfad:** `app/src/main/java/org/htwk/pacing/backend/model2/PredictorModel2.kt`

**Zweck:** Hauptfassade des Modells - koordiniert Training und Vorhersage.

**Design Pattern:** **Facade Pattern** + **Singleton (object)**

#### Wichtige Funktionen:

| Funktion | Beschreibung |
|----------|--------------|
| `train(heartRate, energy)` | Trainiert das Modell mit historischen Daten |
| `predict(input)` | Generiert Vorhersage für aktuellen und zukünftigen Energielevel |
| `aggregateHR(data, minutes)` | Aggregiert HR-Daten in Zeitbuckets |
| `findEnergyAtTime(curve, targetTime)` | Findet Energie zu einem bestimmten Zeitpunkt |
| `fallbackEnergy(validatedEnergy)` | Fallback wenn keine HR-Daten vorhanden |

#### Ablauf `train()`:
1. Konvertiert `HeartRateEntry` → `HRDataPoint`
2. Konvertiert `ValidatedEnergyLevelEntry` → `EnergyDataPoint`
3. Aggregiert HR-Daten (Standard: 15-Minuten-Buckets)
4. Ruft `Optimizer.autoFit()` auf
5. Speichert trainierte Parameter in `trainedParams`
6. Berechnet Decay-Rate via `EnergyDecayFallback.computeDecayRate()`

#### Ablauf `predict()`:
1. Prüft auf HR-Daten → sonst `fallbackEnergy()`
2. Berechnet HRV aus HR-Daten via `HRVDrain.calculateHRVFromHR()`
3. Ruft `EnergyCalculation.calculateEnergyWithHRVDrainAnchored()` auf
4. Findet Energie für "jetzt" und "+2h"
5. Gibt `PredictedEnergyLevelEntryModel2` zurück

---

### 3. SleepDetection.kt

**Pfad:** `app/src/main/java/org/htwk/pacing/backend/model2/SleepDetection.kt`

**Zweck:** Erkennt Schlafphasen anhand der Herzfrequenz.

**Design Pattern:** **Singleton (object)** + **State Machine** (via `foldIndexed`)

#### Funktionen:

| Funktion | Beschreibung |
|----------|--------------|
| `detectSleepPhases(hrAgg, sleepCfg)` | Erkennt Schlafphasen via HR-Schwellwerte |
| `findPeakTimestamp(hrAgg, currentIdx)` | Findet HR-Peak vor Schlafbeginn |
| `detectWakeEvents(hrAgg, sleepCfg)` | Extrahiert Aufwach-Ereignisse |
| `getSleepCycles(hrAgg, sleepCfg)` | Generiert Schlafzyklen zwischen Phasen |

#### Mathematisches Konzept:

**Schwellwert-basierte Zustandsmaschine:**
```
Zustand: WACH ──[HR < sleepHRThreshold (62 bpm)]──► SCHLAFEND
Zustand: SCHLAFEND ──[HR ≥ wakeHRThreshold (70 bpm)]──► WACH
```

Die Funktion `findPeakTimestamp()` schaut bis zu 20 Datenpunkte zurück, um den HR-Peak vor dem Einschlafen zu finden (realistischerer Schlafbeginn).

---

### 4. HRVDrain.kt

**Pfad:** `app/src/main/java/org/htwk/pacing/backend/model2/HRVDrain.kt`

**Zweck:** Berechnet HRV (Herzratenvariabilität) und den Drain-Multiplikator.

**Design Pattern:** **Singleton (object)**

#### Funktionen:

| Funktion | Beschreibung |
|----------|--------------|
| `calculateHRVFromHR(hrData, windowMinutes)` | Berechnet RMSSD-Approximation aus HR |
| `calculateHRVBaseline(hrvData)` | Berechnet Median-Baseline der HRV |
| `getHRVAtTime(hrvData, targetTime)` | Findet HRV zu bestimmtem Zeitpunkt |
| `getDrainMultiplier(currentHRV, baseline, config)` | Berechnet Drain-Multiplikator |

#### Mathematisches Konzept - RMSSD:

**RMSSD (Root Mean Square of Successive Differences):**
```
RMSSD = √(Σ(HR[i] - HR[i-1])² / (n-1))
```

> **Hinweis:** Dies ist eine Approximation, da echte HRV aus RR-Intervallen (Millisekunden zwischen Herzschlägen) berechnet wird, nicht aus BPM.

#### Drain-Multiplikator-Logik:
```
ratio = currentHRV / baseline

if ratio < 0.7:  → 1.5x Drain (niedriges HRV = Stress)
if ratio > 1.3:  → 0.5x Drain (hohes HRV = Erholung)
else:            → 1.0x Drain (normal)
```

---

### 5. EnergyCalculation.kt

**Pfad:** `app/src/main/java/org/htwk/pacing/backend/model2/EnergyCalculation.kt`

**Zweck:** Kernberechnung der Energie mit HRV-basiertem Drain und Anchor-Punkten.

**Design Pattern:** **Singleton (object)** + **Segment-basierte Berechnung**

#### Funktionen:

| Funktion | Beschreibung |
|----------|--------------|
| `calculateEnergyWithHRVDrainAnchored()` | **HAUPT-ENTRY-POINT** - Energie mit Anchoring |
| `calculateEnergyWithHRVDrain()` | Berechnet Energie für ein Segment |
| `calculateEnergyChange()` | Berechnet Energieänderung pro Zeitschritt |
| `buildAnchorPoints()` | Baut Anchor-Punkte aus validierten Eingaben |
| `filterDataForSegment()` | Filtert Daten für ein Segment |

#### Mathematisches Konzept - Energieberechnung:

```kotlin
when {
    hr < hrLow → {
        // RECOVERY (Erholung)
        energy += (hrLow - hr) × 0.1 × recoveryFactor × timeFactor
    }
    hr > hrHigh → {
        // DRAIN (Verbrauch)
        energy -= (hr - hrHigh) × 0.15 × drainFactor × hrvMultiplier × timeFactor
    }
    else → {
        // NEUTRAL (keine Änderung)
        energy = currentEnergy
    }
}

timeFactor = deltaMinutes / 15.0  // Normalisierung auf 15-Minuten-Basis
```

**Grafische Darstellung:**
```
Energie-Änderung
    ▲
    │        DRAIN
    │         ╱
────┼────────╱──────────────► HR (bpm)
    │       ╱
    │hrLow hrHigh
    │╲
    │ ╲
    │  RECOVERY
    ▼
```

#### Anchor-Konzept:
Anchor-Punkte sind nutzer-validierte Energie-Eingaben, die als "Korrekturen" dienen:
```
Zeit ─────────────────────────────────────►
      │         │              │
   Anchor1   Anchor2        Anchor3
   (Start)   (Korrektur)    (Korrektur)
      │         │              │
      ▼         ▼              ▼
   Segment1  Segment2       Segment3
   (Simulation von Anchor zu Anchor)
```

---

### 6. EnergyDecayFallback.kt

**Pfad:** `app/src/main/java/org/htwk/pacing/backend/model2/EnergyDecayFallback.kt`

**Zweck:** Fallback-Vorhersage wenn keine HR-Daten verfügbar sind.

**Design Pattern:** **Singleton (object)** + **Time-of-Day Strategie**

#### Funktionen:

| Funktion | Beschreibung |
|----------|--------------|
| `computeDecayRate(energyData)` | Berechnet personalisierte Abklingrate |
| `getDecayForHour(rate, hour)` | Liefert Decay-Rate für Stunde |
| `predictWithDecay(lastEnergy, lastTime, now, decayRate)` | Vorhersage ohne HR-Daten |
| `defaultDecayRate()` | Standard-Decay (3%/h) |

#### Mathematisches Konzept - Decay-Berechnung:

**Algorithmus:**
1. Sortiere Energie-Einträge nach Zeit
2. Bilde konsekutive Paare
3. Berechne Energieänderung pro Stunde: `changePerHour = (E₂ - E₁) / Δt`
4. Filtere Paare mit Δt < 6min oder Δt > 12h
5. Gruppiere nach Tageszeit
6. Berechne Median pro Gruppe

**Tageszeit-Buckets:**

| Bucket | Stunden | Typisches Verhalten |
|--------|---------|---------------------|
| Morning | 06-11 | Moderater Drain |
| Afternoon | 12-17 | Hoher Drain |
| Evening | 18-21 | Hoher Drain |
| Night | 22-05 | Recovery (negativ) |

---

### 7. Optimizer.kt

**Pfad:** `app/src/main/java/org/htwk/pacing/backend/model2/Optimizer.kt`

**Zweck:** Parameteroptimierung mittels Grid Search und Nelder-Mead.

**Design Pattern:** **Singleton (object)** + **Strategy Pattern** (für Aggregation)

#### Wichtige Funktionen:

| Funktion | Beschreibung |
|----------|--------------|
| `groupBySleepCycle()` | Gruppiert Daten nach Schlafzyklen |
| `calculateCycleLoss()` | Berechnet MSE für Parameter |
| `calculateEnergyOffset()` | Berechnet Energie-Offset |
| `gridSearchCycle()` | Grid-Search Optimierung |
| `nelderMeadCycle()` | Nelder-Mead Feinoptimierung |
| `autoFit()` | Hauptfunktion: Kombiniert alles |
| `simulateEnergy()` | Simuliert Energie ohne Offset |
| `median()` | Median-Berechnung |

#### Mathematisches Konzept - Nelder-Mead:

**Nelder-Mead Simplex-Algorithmus** (ableitungsfreie Optimierung):

Ein **Simplex** ist ein geometrisches Objekt mit n+1 Eckpunkten im n-dimensionalen Raum. Hier: 5 Punkte für 4 Parameter (hrLow, hrHigh, drainFactor, recoveryFactor).

**Operationen:**
```
1. REFLECTION (α=1.0):
   x_reflected = centroid + α × (centroid - worst)

2. EXPANSION (γ=1.0):
   x_expanded = centroid + γ × (reflected - centroid)

3. CONTRACTION (ρ=0.5):
   x_contracted = centroid + ρ × (worst - centroid)

4. SHRINK (σ=0.5):
   Alle Punkte außer best werden Richtung best gezogen
```

**Visualisierung:**
```
                  ×best
                 /|\
                / | \
               /  |  \
              /   |   \        ← Simplex (Dreieck in 2D)
             /    |    \
            ×────────────×
         second-worst  worst

Nach Reflection:
                  ×best
                 /|\
                / | \
               /  ×  \        ← reflected (jenseits centroid)
              / centroid\
             /           \
            ×─────────────×
```

#### Loss-Funktion (MSE):

```
loss = Σ(predicted - validated)² / n
```

#### Grid-Search Parameter:

| Parameter | Werte |
|-----------|-------|
| hrLow | [50, 55, 60, 65, 70] |
| hrHigh | [75, 80, 90, 100, 110, 120, 130] |
| drainFactor | [0.5, 1.0, 1.5, 2.0, 2.5, 3.0] |
| recoveryFactor | [0.5, 1.0, 1.5, 2.0, 2.5, 3.0] |

**Kombinationen:** 5 × 7 × 6 × 6 = **1.260** Kombinationen pro Zyklus

---

## Sequenzdiagramm: Vollständiger Vorhersageablauf

```
┌──────────┐    ┌─────────────────┐    ┌───────────────┐    ┌─────────────┐    ┌────────────────────┐
│  Caller  │    │ PredictorModel2 │    │   HRVDrain    │    │ Optimizer   │    │ EnergyCalculation  │
└────┬─────┘    └───────┬─────────┘    └──────┬────────┘    └──────┬──────┘    └─────────┬──────────┘
     │                  │                     │                    │                     │
     │ train(hr, energy)│                     │                    │                     │
     ├─────────────────►│                     │                    │                     │
     │                  │ aggregateHR()       │                    │                     │
     │                  ├──────────────┐      │                    │                     │
     │                  │              │      │                    │                     │
     │                  │◄─────────────┘      │                    │                     │
     │                  │                     │                    │                     │
     │                  │ autoFit()           │                    │                     │
     │                  ├─────────────────────┼───────────────────►│                     │
     │                  │                     │    groupBySleepCycle()                   │
     │                  │                     │◄───────────────────┤                     │
     │                  │                     │    detectSleepPhases()                   │
     │                  │                     │                    │                     │
     │                  │                     │    gridSearchCycle()                     │
     │                  │                     │                    ├──────────────┐      │
     │                  │                     │                    │              │      │
     │                  │                     │                    │◄─────────────┘      │
     │                  │                     │    nelderMeadCycle()                     │
     │                  │                     │                    ├──────────────┐      │
     │                  │                     │                    │              │      │
     │                  │                     │                    │◄─────────────┘      │
     │                  │                     │                    │                     │
     │                  │◄────────────────────┼────────────────────┤                     │
     │                  │ OptimizationResult  │                    │                     │
     │◄─────────────────┤                     │                    │                     │
     │                  │                     │                    │                     │
     │ predict(input)   │                     │                    │                     │
     ├─────────────────►│                     │                    │                     │
     │                  │ calculateHRVFromHR()│                    │                     │
     │                  ├────────────────────►│                    │                     │
     │                  │◄────────────────────┤ List<HRVPoint>     │                     │
     │                  │                     │                    │                     │
     │                  │ calculateEnergyWithHRVDrainAnchored()    │                     │
     │                  ├─────────────────────┼────────────────────┼────────────────────►│
     │                  │                     │                    │                     │
     │                  │                     │ getHRVAtTime()     │                     │
     │                  │                     │◄────────────────────────────────────────┤
     │                  │                     │────────────────────────────────────────►│
     │                  │                     │                    │                     │
     │                  │                     │ getDrainMultiplier()                    │
     │                  │                     │◄────────────────────────────────────────┤
     │                  │                     │────────────────────────────────────────►│
     │                  │                     │                    │                     │
     │                  │◄────────────────────┼────────────────────┼─────────────────────┤
     │                  │ List<EnergyResultWithHRV>                │                     │
     │◄─────────────────┤                     │                    │                     │
     │  PredictedEnergy │                     │                    │                     │
```

---

## Design Patterns im Überblick

| Pattern | Verwendung | Datei/Klasse |
|---------|------------|--------------|
| **Singleton** | Alle Hauptobjekte (`object`) | Alle Hauptklassen |
| **Facade** | `PredictorModel2` versteckt Komplexität | `PredictorModel2.kt` |
| **Strategy** | Aggregationsfunktion als Parameter | `Optimizer.autoFit()` |
| **State Machine** | Schlaferkennung mit Zuständen | `SleepDetection.detectSleepPhases()` |
| **Template Method** | Anchor-basierte Segmentierung | `EnergyCalculation` |
| **Builder-like** | Anchor-Punkt-Aufbau | `buildAnchorPoints()` |

---

## Mathematische Konzepte Zusammenfassung

| Konzept | Verwendung | Formel/Beschreibung |
|---------|------------|---------------------|
| **RMSSD** | HRV-Berechnung | √(Σ(HR[i]-HR[i-1])²/(n-1)) |
| **Median** | Robuste Aggregation | Mittlerer Wert sortierter Liste |
| **MSE (Mean Squared Error)** | Loss-Funktion | Σ(predicted-actual)²/n |
| **Nelder-Mead** | Parameteroptimierung | Simplex-basierte Optimierung |
| **Grid Search** | Initiale Parametersuche | Erschöpfende Suche |
| **Schwellwert-Hysterese** | Schlaferkennung | Unterschiedliche Ein-/Aus-Schwellen |
| **Lineare Interpolation** | Energie-Änderung | ΔE = f(HR) × Δt |
| **Time-of-Day Strategie** | Decay-Rate | Unterschiedliche Raten nach Tageszeit |

---

## Wichtige Konstanten und Defaultwerte

| Konstante | Wert | Bedeutung |
|-----------|------|-----------|
| `TIME_SERIES_DURATION` | 2 Stunden | Vorhersage-Horizont |
| `sleepHRThreshold` | 62 bpm | HR unter der man als schlafend gilt |
| `wakeHRThreshold` | 70 bpm | HR über der man als wach gilt |
| `minSleepMinutes` | 200 min | Mindestdauer für gültige Schlafphase |
| `hrLow` (default) | 59.5 bpm | Untere HR-Grenze für Recovery |
| `hrHigh` (default) | 83.2 bpm | Obere HR-Grenze für Drain |
| `timeOffsetMinutes` | 120 min | Verzögerung HR → Energie |
| `aggregationMinutes` | 15 min | Bucket-Größe für HR-Aggregation |
| `DEFAULT_HOURLY_DECAY` | 3%/h | Fallback Decay-Rate |

---

## Dateipfade

```
app/src/main/java/org/htwk/pacing/backend/model2/
├── Types.kt              # Datenklassen (DTOs)
├── PredictorModel2.kt    # Hauptfassade
├── SleepDetection.kt     # Schlaferkennung
├── HRVDrain.kt           # HRV-Berechnung
├── EnergyCalculation.kt  # Kern-Energieberechnung
├── EnergyDecayFallback.kt # Fallback ohne HR
└── Optimizer.kt          # Parameteroptimierung
```

---

*Generiert am: Januar 2025*
