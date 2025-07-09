# Health Connect Integration – SettingsScreen & HeartRateScreen

## Zweck

Diese Komponenten integrieren Google Health Connect in eine Compose-basierte Android-App. Sie ermöglichen das Abrufen und Anzeigen von Herzfrequenz- und Schrittzahl-Daten und verwalten die nötigen Berechtigungen.

---

## Anforderungen

- Health Connect App (`com.google.android.apps.healthdata`) installiert
- `HeartRateRecord`- und `StepsRecord`-Leseberechtigungen
- Android 10+ empfohlen

---

## UI Struktur

- `SettingsScreen` → `HealthConnectItem` → `HeartRateScreen`
- Layouts: `Box` mit Rahmen & Ecken, `Column` für vertikale Anordnung
