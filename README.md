# Nota

**Nota** is an Android music player that brings your local music library and online music discovery together in one place. It is built with Kotlin, Jetpack Compose, AndroidX Media3, and a custom Liquid Glass interface.

> **Current release: v0.3.0**
> Android 8.0 (API 26) and newer.

## Features

### Music playback
- Scan and play audio stored on your device with Android MediaStore.
- Background playback through Media3 `MediaSessionService`, with notification and lock-screen controls.
- Queue management, play/pause, next/previous, shuffle, repeat, and precise seeking.
- Home-screen playback widget.
- Sleep timer.

### Online music
- Search for online music and stream audio through NewPipe Extractor, with Piped instances as a fallback.
- Save online tracks for offline listening.
- Choose app-private storage or your public Music folder for offline downloads.
- Track download progress and manage saved tracks.

### Library and playlists
- Search your local library.
- Create and manage custom playlists.
- Favorites and Most Played smart playlists.
- Recently Played tracking.

### Lyrics and translation
- Read embedded lyrics, import local `.lrc` files, and look up synced lyrics online through supported providers.
- Generate word-timed lyrics from downloaded audio with **Gemini** or **Groq Whisper** (an API key from the selected provider is required).
- Translate lyrics into English, Russian, Azerbaijani, or Turkish using the app's online translation fallback services.

### Interface
- Dark-first monochrome visual identity and the Nota adaptive launcher icon.
- Liquid Glass surfaces, floating mini-player, and animated bottom navigation.
- System, light, and dark theme support.

## Download

Get the latest APK from [GitHub Releases](https://github.com/XelilovTh/Nota/releases).

For this version, download **Nota-v0.3.0.apk** from the [v0.3.0 release page](https://github.com/XelilovTh/Nota/releases/tag/v0.3.0).

To install manually:
1. Download the APK to your Android device.
2. Allow your browser or file manager to install apps from that source if Android asks.
3. Open the downloaded APK and follow the installation steps.

## Optional AI lyrics setup

AI transcription is optional. To generate word-synchronized lyrics:
1. Open Nota's lyrics controls while playing a downloaded track.
2. Select Gemini or Groq Whisper.
3. Add your own API key for the selected provider when prompted.

API keys are user-provided inside the app. Do not commit API keys or other credentials to this repository.

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose, Material 3, Haze
- **Playback:** AndroidX Media3 / ExoPlayer and MediaSession
- **Architecture:** Clean Architecture, MVVM, Hilt
- **Online extraction:** NewPipe Extractor with Piped fallback
- **Build:** Gradle Kotlin DSL, Version Catalog, JDK 17
- **Android:** minSdk 26, targetSdk 35

## Project structure

- `data/` — MediaStore, playback service, online music, lyrics, and repository implementations.
- `domain/` — Models, repository contracts, and use cases.
- `presentation/` — Compose screens and ViewModels.
- `ui/theme/` — Centralized colors, typography, shapes, and dimensions.
- `ui/components/` — Reusable glass components and playback UI.
- `di/` — Hilt dependency-injection modules.
- `widget/` — Android home-screen music widget.

## Build and verification

Build, lint, and test commands:

```bash
./gradlew --no-daemon assembleDebug
./gradlew --no-daemon lint
./gradlew --no-daemon testDebugUnitTest
./gradlew --no-daemon assembleRelease
```

GitHub Actions runs the debug build, lint, unit tests, signed release build, and APK signature verification on pushes and pull requests. Build artifacts and reports are available from the [Actions page](https://github.com/XelilovTh/Nota/actions).

## License

No license has been specified for this repository yet. All rights remain with the project owner unless a license is added.
