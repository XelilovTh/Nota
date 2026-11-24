# Nota

A clean Android application foundation. This repository currently contains only the app shell and build infrastructure.

## Stack
- Kotlin + Jetpack Compose
- Gradle Kotlin DSL and version catalog
- Hilt dependency injection
- Navigation Compose
- minSdk 26 / targetSdk 35

## Structure
- data — data sources and repositories
- domain — application models and use cases
- presentation — app navigation and screen composition
- ui/theme — the single source of truth for colors, typography, shapes and dimensions
- ui/components — reusable UI components
- di — Hilt modules

## Build
- ./gradlew assembleDebug
- ./gradlew lint
- ./gradlew testDebugUnitTest

GitHub Actions runs all three checks on pushes and pull requests.
