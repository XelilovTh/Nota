# Nota Architecture

This document maps Nota's codebase, data flows, and critical design decisions so any developer or AI agent can navigate and modify the project safely.

## 1. Layering & Package Map

```
com.xelilovth.nota
├── data/
│   ├── lyrics/        LyricsParser.kt, LyricsRepositoryImpl.kt
│   ├── media/         AudioMediaStore.kt, MediaStoreScanner.kt, PlaybackNavigation.kt,
│   │                  PlaybackPlayerHolder.kt, PlaybackService.kt, PlaybackStateMapper.kt,
│   │                  SleepTimerManager.kt
│   └── repository/    CustomPlaylistRepositoryImpl.kt, LibraryRepositoryImpl.kt,
│                      PlaybackRepositoryImpl.kt, SettingsRepositoryImpl.kt
├── di/                DispatcherModule, LibraryModule, LyricsModule, PlaybackModule,
│                      PlaylistModule, SettingsModule
├── domain/
│   ├── model/         AppSettings, AutoPlaylist, CustomPlaylist, DeleteTrackResult,
│   │                  PlaybackState, Track, TrackLyrics, TrackMetadataOverride, TrackSearch
│   ├── repository/    CustomPlaylistRepository, LibraryRepository, LyricsRepository,
│   │                  PlaybackRepository, SettingsRepository
│   └── usecase/       CycleRepeatModeUseCase, ObserveTracksUseCase, PlayTrackUseCase,
│                      ScanLibraryUseCase, SeekToUseCase, SkipNextUseCase,
│                      SkipPreviousUseCase, TogglePlayPauseUseCase, ToggleShuffleUseCase
├── presentation/
│   ├── NotaApp.kt     Root shell, NavHost, MiniPlayerHost, LiquidGlassBottomBar, Modal Player Overlay
│   ├── AppRoute.kt    Route constants & bottom tab definitions
│   ├── home/          HomeScreen, HomeViewModel
│   ├── library/       LibraryScreen, LibraryViewModel
│   ├── player/        NowPlayingScreen, MiniPlayer, LyricsPanel, LyricsUiState, PlayerViewModel
│   ├── playlists/     PlaylistsScreen, PlaylistDetailScreen, AddToPlaylistSheet, PlaylistsViewModel
│   ├── search/        SearchScreen
│   └── settings/      SettingsScreen, SleepTimerScreen, SettingsViewModel
├── ui/
│   ├── components/    GlassComponents, LiquidGlassBottomBar, TrackArtwork, ComingSoonScreen
│   └── theme/         Color.kt, Dimens.kt, Shape.kt, Theme.kt, Type.kt
└── widget/            NotaWidgetProvider.kt
```

## 2. Playback & Seek Pipeline (`data/media/`)
- **`PlaybackPlayerHolder.kt`**:
  - Configures `DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)` (without `Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING`, which breaks Xing/VBRI VBR MP3 seeking).
  - Sets `player.setSeekParameters(SeekParameters.EXACT)` on `ExoPlayer` so seeks land on the exact requested frame instead of drifting earlier/later.
- **`PlaybackService.kt`**: Media3 `MediaSessionService` managing background playback, notification controls, and audio focus.
- **`PlaybackRepositoryImpl.kt`**: Bridges Media3 `Player` state (`PlaybackStateMapper`) with domain `StateFlow<PlaybackState>`, listening-time tracking, and `Recently Played` / `Most Played` stats.

## 3. Navigation & Modal Player (`presentation/NotaApp.kt`)
- **Bottom Tabs (`NavHost`)**: `Home`, `Playlists`, `Search`, `Library`, plus detail/settings routes (`PlaylistDetail`, `Settings`, `SleepTimer`).
- **Floating Chrome**: `MiniPlayerHost` + `LiquidGlassBottomBar` sit in a bottom-aligned `Column` over `NavHost`, sharing a `HazeState` backdrop.
- **Modal `NowPlayingScreen` Overlay**:
  - Controlled by `isPlayerOpen` (`rememberSaveable`) and a single `Animatable` (`playerOffsetY`) inside `BoxWithConstraints`.
  - **Not inside `NavHost`**: Using a single `Animatable` eliminates the snap-back race condition between `NavHost` exit transitions and screen-level drag offsets.
  - `BackHandler(enabled = isPlayerOpen)` closes the overlay smoothly.
  - Both the root `NowPlayingScreen` container and `LyricsSwipeHint` forward downward drags to `onVerticalDrag` / `onVerticalDragEnd` / `onVerticalDragCancel`.

## 4. Lyrics & Translation Pipeline (`data/lyrics/`)
- **`LyricsParser.kt`**:
  - Parses TTML (`fromTtml`), Enhanced LRC (`<mm:ss.xx>` word-level sync), standard `[mm:ss.xx]` LRC, and plain lyrics.
  - Strips any residual `[mm:ss.xx]` or `<mm:ss.xx>` tags from `LyricLine.text` and `LyricWord.text` so raw timestamps never render in the UI.
- **`LyricsRepositoryImpl.kt`**:
  - **Fetch order**: In-memory cache -> disk cache (`filesDir/lyrics_cache/<trackId>.json`) -> `BetterLyrics` -> `LRCLIB` -> `lyrics.ovh`.
  - **Gemini Audio Transcription (`generateLyricsWithGemini`)**: Optional user-supplied Gemini API key (`gemini-2.5-flash`) generates Enhanced LRC with word-level timestamps. Never auto-runs without user action.
  - **Translation (`translateLyrics`)**:
    - **Does NOT use Gemini** (preserves Gemini API quota).
    - Checks per-language cache (`LyricLine.translations[targetLanguage]`) first.
    - Deduplicates concurrent requests (`inFlightTranslations`), serializes remote calls (`translationMutex`), throttles (`600ms` min interval), and batches lines into ~900-char chunks.
    - Multi-tier online fallback chain: **Google Translate (`translate.googleapis.com`, `client=gtx`)** -> **Lingva (`lingva.ml`, `lingva.lunar.icu`)** -> **MyMemory (`api.mymemory.translated.net`)**.
    - Supported target languages: `English`, `Russian`, `Azerbaijani`, `Turkish`.

## 5. Playlists & Listening Stats
- **Auto-Playlists (`AutoPlaylist`)**:
  - `FAVORITES`: All tracks marked favorite.
  - `MOST_PLAYED`: Sorted by cumulative listening duration (`totalListeningMs`), not tap count.
- Both `Favorites` and `Most Played` cards in `PlaylistsScreen` navigate to `PlaylistDetailScreen` instead of immediately hijacking playback.
