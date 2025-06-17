# Pacing App

Jetpack Compose app for user interactions, collection of wearable data and interpretation of ML results.

## Dependencies

- Android Studio
- Git

## Project Structure

- app/src
  - main: Kotlin source code
    - java/org/htwk/pacing: android app source code
      - ui: user interface, screen, components, themes, etc.
      - backend: database connection, data collection/generation, etc.
      - Application.kt: Entry point for non ui work (database, workers, etc.)
      - MainActivity.kt: Entry point for ui
    - res: icons, string localization, etc.
  - androidTest: tests that run on an android emulator
  - test: tests that run on a local machine/server
  - build.gradle.kts: packages to compile app with
- app/schemas: Full database schemas exported from room (seperate file for each version)
- gradle
  - libs.version.toml: external package list + versions
  - wrapper: current gradle executable
