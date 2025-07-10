# Health Connect Integration – SettingsScreen & HeartRateScreen

## Zweck

Diese Komponenten integrieren Google Health Connect in eine Compose-basierte Android-App. Der Zugriff auf Gesundheitsdaten erfolgt zentral über eine gekapselte Hilfsklasse (HeathConnectHelper) die per Worker stündlich aktuslisiert werden. Die Komponenten verwaltet die nötigen Berechtigungen, zeigen aggregierte und rohe Herzfrequenz- und Schrittzahldaten an und unterstützten optional den CSV-Import.

---

## Anforderungen

- Health Connect App (`com.google.android.apps.healthdata`) installiert
- `HeartRateRecord`- und `StepsRecord`-Leseberechtigungen
- Schreibberechtigung für `HeartRateRecord` nur bei CSV-Import notwendig
- Android 10+ empfohlen

---

## UI Struktur

- `SettingsScreen` → `HealthConnectItem` → `HeartRateScreen`
- Layouts: `Box` mit Rahmen & Ecken, `Column` für vertikale Anordnung
