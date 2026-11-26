# AGENTS.md — Nota AI Agent Playbook

Read this file **first** before modifying any code in `Nota`. Follow every rule below strictly so all agents (Claude, GPT, Gemini, Codex, Cursor, Antigravity) work consistently without breaking existing design or behavior.

## 1. Required Reading Order
1. `AGENTS.md` (this file)
2. `PROJECT_RULES.md` (build gates, git rules, permissions)
3. `docs/ARCHITECTURE.md` (exact package/module map & data flows)
4. `docs/DESIGN_SYSTEM.md` (Liquid Glass UI & `ui/theme/` token rules)

## 2. Non-Negotiable Code Rules
1. **Zero Hard-Coded Design Tokens**:
   - **NEVER** write `.dp`, `.sp`, or `Color(...)` outside `app/src/main/java/com/xelilovth/nota/ui/theme/`.
   - Always add/use tokens in `AppDimens` (`ui/theme/Dimens.kt`), `Color.kt`, `Type.kt`, `Shape.kt`.
   - Before committing, run:
     `rg -n "\.dp\b|\.sp\b|Color\(" app/src/main/java/com/xelilovth/nota/ --glob "!**/ui/theme/**"`
2. **Liquid Glass UI Consistency**:
   - All cards, bars, sheets, and dialogs **must** use `GlassSurface` or `GlassDialog` from `ui/components/GlassComponents.kt` (never raw `AlertDialog` or flat opaque `Surface` for modals/cards).
   - Respect `LocalBottomContentPadding` (`Theme.kt`) on scrollable lists so content clears the floating `MiniPlayer` + `LiquidGlassBottomBar`.
3. **Architecture & DI**:
   - Clean Architecture + MVVM + Hilt (`data/` -> `domain/` -> `presentation/` + `ui/`).
   - ViewModels inject domain repositories/use cases, never Android `Context` directly (use repositories).
   - Always rethrow `kotlinx.coroutines.CancellationException` in `try/catch` blocks inside `suspend` functions.
4. **Build & Verification**:
   - Note: On the owner's Windows machine there is **no local Android SDK** (`ANDROID_HOME` / `local.properties` absent).
   - Verify static checks locally (`rg` token check, `git diff`) and push to GitHub Actions (`.github/workflows/android-build.yml`), which runs `./gradlew --no-daemon assembleDebug lint testDebugUnitTest`, `assembleRelease`, and `apksigner verify` on every `push` and `pull_request` to any branch.
   - If a connected phone is available via `adb devices`, test live UI/UX flows via `adb shell` (`screencap`, `uiautomator dump`, `logcat`).

## 3. Critical Architectural Guardrails (Do NOT Break These!)
- **NowPlaying Modal Overlay (`presentation/NotaApp.kt`)**:
  - `NowPlayingScreen` is a **single-`Animatable` (`playerOffsetY`) modal overlay** in `NotaApp.kt`, **NOT** a `NavHost` destination. Moving it back into `NavHost` causes swipe-down dismiss snap-back glitches.
  - Both root vertical drags and `LyricsSwipeHint` downward drags forward to `onVerticalDrag` / `onVerticalDragEnd` / `onVerticalDragCancel` in `NotaApp.kt`.
- **NowPlaying Layout Anchoring (`presentation/player/NowPlayingScreen.kt`)**:
  - Keep `Spacer(modifier = Modifier.height(AppDimens.PlayerControlsBottomSpacing))` below the Play/Pause `GlassSurface` and `Modifier.weight(1f)` on the Lyrics/Hint `Box`. Never use a `Modifier.weight(...)` spacer below Play/Pause, or expanding lyrics will shift the Play/Pause controls upward.
  - Progress seeking on the artwork is strictly limited to the circular arc track (`CircularArcPlayerArt`), not dragging on the inner album artwork.
- **MP3 / Audio Seek Precision (`data/media/PlaybackPlayerHolder.kt`)**:
  - Use `DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)` and `player.setSeekParameters(SeekParameters.EXACT)`.
  - **Do NOT** re-enable `Mp3Extractor.FLAG_ENABLE_INDEX_SEEKING` — it conflicts with Xing/VBRI headers and causes inaccurate forward/backward seeks.
- **Lyrics & Translation (`data/lyrics/LyricsRepositoryImpl.kt`)**:
  - **Lyrics transcription (audio -> LRC)** uses Gemini (`generateLyricsWithGemini`) with Enhanced LRC (`<mm:ss.xx>`) word-level timestamps and requires the user's Gemini API key.
  - **Lyrics translation** (`translateLyrics`) must **NOT** call Gemini (to save API quota). It uses the multi-tier free online chain (**Google Translate `gtx`** -> **Lingva** -> **MyMemory**) with `inFlightTranslations` deduplication, `translationMutex`, `600ms` request throttling, ~900-char chunking, and per-language disk/memory caching (`English`, `Russian`, `Azerbaijani`, `Turkish`).
  - LRC/TTML parsing (`LyricsParser.kt`) must strip any leaked inline `[mm:ss.xx]` or `<mm:ss.xx>` timestamp tags from displayed `Line.text` and `Word.text`.
- **Smart Playlists (`presentation/playlists/`)**:
  - `Favorites` and `Most Played` cards open `PlaylistDetailScreen` (auto-playlist route) showing the track list rather than immediately starting playback.
  - `Most Played` is sorted by cumulative listening time (`totalListeningMs`), not simple click count.
