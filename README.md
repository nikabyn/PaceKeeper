# Pacing App

Jetpack Compose app for user interactions, collection of wearable data and interpretation of ML
results.

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
- app/schemas: Full database schemas exported from room (separate file for each version)
- gradle
    - libs.version.toml: external package list + versions
    - wrapper: current gradle executable

## Naming conventions

We are using exclusively English for source and commit messages.
See also: https://gitlab.dit.htwk-leipzig.de/groups/pacing-app/-/wikis/Konventionen/Git

Classes, Interfaces, Objects and @Composable Functions are named using `PascalCase`:

```kotlin
data class DataEntry()
interface Graphable {}
object Companion {}
```

Functions, Methods, Variables and Parameters are named using `camelCase`:

```kotlin
fun testFunction(paramOne: Double) {}
fun SomeClass.extensionFunction() {}

val databaseConnection = Database.createConnection()
var person = Person()
```

Constants are named using `UPPERCASE`:

```kotlin
const val TAG = "SomeName"
```

These rules can also be found in the
official [Kotlin Styleguide](https://kotlinlang.org/docs/coding-conventions.html)

## Git workflow

Please read through our process before you start:

- https://gitlab.dit.htwk-leipzig.de/groups/pacing-app/-/wikis/Guides/Git-Flow
- https://gitlab.dit.htwk-leipzig.de/groups/pacing-app/-/wikis/Konventionen/Git