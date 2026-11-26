# Nota

**Nota** is a modern offline-first Android music player built with Kotlin, Jetpack Compose, Media3 (ExoPlayer), and a custom Liquid Glass UI.

## Key Features
- **Local Audio Library**: MediaStore scanning, search, metadata overrides, track deletion, and quick-play/shuffle.
- **Media3 Playback**: Background `MediaSessionService`, gapless/exact MP3 seeking (`SeekParameters.EXACT`), queue management, repeat/shuffle modes, and widget support (`NotaWidgetProvider`).
- **Smart & Custom Playlists**: Custom user playlists plus auto-playlists for **Favorites** and **Most Played** (ranked by cumulative listening time).
- **Synced & Word-Level Lyrics**:
  - Embedded tags, local `.lrc` import, and online lookup (`BetterLyrics` TTML, `LRCLIB`, `lyrics.ovh`).
  - **Gemini Audio Transcription**: Optional Gemini API key (`gemini-2.5-flash`) to transcribe songs into Enhanced LRC with word-by-word sync.
  - **Multi-Tier Offline-Cached Translation**: Translates lyrics into **English**, **Russian**, **Azerbaijani**, or **Turkish** via Google Translate (`gtx`) -> Lingva -> MyMemory with rate-limit throttling and persistent per-language caching (no Gemini quota spent).
- **Liquid Glass UI**: Floating `MiniPlayer`, `LiquidGlassBottomBar` with drag-to-switch lens physics, `BreathingAmbientBackground`, modal `NowPlayingScreen`, and glass dialogs/sheets.
- **Sleep Timer**: Configurable countdown timer integrated with playback.

## Tech Stack
- **Language / UI**: Kotlin, Jetpack Compose (Material 3)
- **Media**: AndroidX Media3 (`ExoPlayer`, `MediaSessionService`)
- **DI**: Hilt (`dagger.hilt.android`)
- **Navigation**: Navigation Compose (`AppRoute`) + Modal `NowPlayingScreen` overlay
- **Build**: Gradle Kotlin DSL + Version Catalog, `minSdk 26` / `targetSdk 35`, JDK 17

## Project Structure
- `data/` — MediaStore scanner, Media3 player/service, `LyricsParser`, `LyricsRepositoryImpl`, repositories
- `domain/` — Domain models (`Track`, `TrackLyrics`, `PlaybackState`, `AutoPlaylist`, `CustomPlaylist`) & use cases
- `presentation/` — `NotaApp`, `HomeScreen`, `PlaylistsScreen`, `SearchScreen`, `LibraryScreen`, `NowPlayingScreen`, `SettingsScreen`, `SleepTimerScreen`, ViewModels
- `ui/theme/` — **Single source of truth** for colors, typography, shapes, and dimensions (`Dimens.kt`, `Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`)
- `ui/components/` — Reusable `GlassSurface`, `GlassDialog`, `LiquidGlassBottomBar`, `TrackArtwork`
- `di/` — Hilt modules
- `widget/` — Home screen music widget (`NotaWidgetProvider`)

## Documentation for Developers & AI Agents
- [[AGENTS|AGENTS.md]] — Mandatory playbook for AI coding agents
- [[PROJECT_RULES|PROJECT_RULES.md]] — Engineering rules, quality gates, and Git/CI rules
- [[docs/ARCHITECTURE|ARCHITECTURE.md]] — Detailed architecture, data flows, and guardrails
- [[docs/DESIGN_SYSTEM|DESIGN_SYSTEM.md]] — Liquid Glass & `ui/theme/` token reference

## Build & CI
```bash
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon lint
./gradlew --no-daemon testDebugUnitTest
```
GitHub Actions (`.github/workflows/android-build.yml`) runs debug build, lint, unit tests, and signed release APK verification on every `push` and `pull_request` to any branch.
