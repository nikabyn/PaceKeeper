# Health Connect Integration \u2013 SettingsScreen & HeartRateScreen

## Zweck

Diese Komponenten integrieren Google Health Connect in eine Compose-basierte Android-App. Sie erm\366glichen das Abrufen und Anzeigen von Herzfrequenz- und Schrittzahl-Daten und verwalten die n\366tigen Berechtigungen.

---

## Komponenten

### `SettingsScreen`

- Verwaltet die Verbindung zu Health Connect.
- Pr\374ft beim Start und bei `ON_RESUME`, ob alle Berechtigungen vorhanden sind.
- Bietet eine M\366glichkeit, die Health Connect App zu \366ffnen oder Berechtigungen anzufordern.
- Nutzt `HealthConnectItem` als UI-Eintrag.
- Startet `HeartRateScreen` zur Anzeige der Daten.

**Wichtige Elemente:**
- `updateConnectionState()` \u2013 pr\374ft, ob alle erforderlichen Berechtigungen vorhanden sind.
- `requestPermissionsActivity` \u2013 Launcher zum Anfragen der Berechtigungen.
- `LifecycleEventObserver` \u2013 sorgt daf\374r, dass der Status bei R\374ckkehr ins UI aktualisiert wird.

---

### `HealthConnectItem`

- Zeigt Verbindungsstatus ("Connected"/"Not connected") an.
- Button \u201eEdit\u201c \366ffnet Health Connect oder fordert Rechte an.
- Ruft `HeartRateScreen()` zur Anzeige von Gesundheitsdaten auf.

---

### `HeartRateScreen`

- Zeigt:
  - \u2300 Herzfrequenz der letzten 24\u202fh
  - Letzte Herzfrequenz-Messwerte (Zeit + bpm)
  - Schrittanzahl gestern & heute
- L\344dt Daten nur, wenn Berechtigungen vorhanden sind.
- Verpackt UI in `Box` mit Border und abgerundeten Ecken.

**Wichtige Funktionen:**
- `queryData()` \u2013 f\374hrt mehrere `HealthConnectClient`-Abfragen durch (aggregiert & Rohdaten).
- `LaunchedEffect` \u2013 triggert Datenabfrage beim ersten Render, wenn Berechtigungen vorhanden sind.

---

## Anforderungen

- Health Connect App (`com.google.android.apps.healthdata`) installiert
- `HeartRateRecord`- und `StepsRecord`-Leseberechtigungen
- Android 10+ empfohlen

---

## UI Struktur

- `SettingsScreen` \u2192 `HealthConnectItem` \u2192 `HeartRateScreen`
- Layouts: `Box` mit Rahmen & Ecken, `Column` f\374r vertikale Anordnung
