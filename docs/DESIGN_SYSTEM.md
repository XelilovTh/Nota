# Nota Design System (Liquid Glass)

## Golden Rule
**All** colors, typography styles, shapes, and dimensions **must** live in `app/src/main/java/com/xelilovth/nota/ui/theme/`.
Never hardcode `.dp`, `.sp`, or `Color(...)` in screens or components.

## 1. Theme & Tokens (`ui/theme/`)
- **`Dimens.kt` (`AppDimens`)**:
  - Screen & card spacing: `ScreenPadding` (`20.dp`), `ElementSpacing` (`8.dp`), `SectionSpacing` (`16.dp`), `CardPadding` (`16.dp`), `RowVerticalPadding` (`10.dp`)
  - Floating bottom chrome: `FloatingNavHorizontalPadding`, `FloatingNavBottomPadding`, `FloatingNavInnerPadding`, `FloatingMiniPlayerHeight`, `FloatingMiniPlayerGap`, `MiniPlayerPadding`
  - Player layout: `PlayerArtworkFraction` (`0.78f`), `PlayerArtworkSize`, `PlayerControlSpacing`, `PlayerControlsBottomSpacing` (`44.dp`), `PlayerBottomSafePadding` (`32.dp`), `PlayButtonSize`, `SwipeDismissThreshold`, `LyricsDragThreshold`, `LyricsScrubberHeight`
  - Lyrics typography/spacing: `LyricsLineSpacing`, `LyricsContentVerticalPadding`, `LyricsContentHorizontalPadding`, `LyricsWordHorizontalPadding`, `LyricsWordSpacing`
- **`Color.kt`**: Dark-first glass palette (`GlassHighlight`, `GlassBorder`, `GlassSurfaceFill`, primary accent, surface variants).
- **`Shape.kt`**: Rounded pill/card corner radii (`AppShapes`).
- **`Type.kt`**: `Typography` scale (`tnum` tabular numerals used for timestamps).
- **`Theme.kt`**: Provides `NotaTheme` and `LocalBottomContentPadding` for scrollable screens to clear the floating `MiniPlayer` + `LiquidGlassBottomBar`.

## 2. Core Glass Components (`ui/components/`)
- **`GlassSurface`** (`GlassComponents.kt`):
  - Standard container for cards, control pills, buttons, and panels.
  - Supports `shape`, `elevated`, `subtle`, `blurBackdrop`, and `onClick`.
- **`GlassDialog`** (`GlassComponents.kt`):
  - **Mandatory** for all modal dialogs (delete confirmations, lyrics delete confirmation, translation language picker, Gemini API key input, playlist creation/rename).
- **`LiquidGlassBottomBar`** (`LiquidGlassBottomBar.kt`):
  - Floating 4-tab navigation bar (`Home`, `Playlists`, `Search`, `Library`) with animated/draggable glass lens indicator.
  - Do not overwrite drag target state with stale `selectedIndex` snaps during tab transition animations.
- **`BreathingAmbientBackground`**:
  - Ambient artwork-tinted background rendered behind the app/player.

## 3. Player UI Rules (`NowPlayingScreen.kt`)
- **Play/Pause Row**: Anchored at `AppDimens.PlayerControlsBottomSpacing` (`44.dp`) above the `Queue` / `Sleep Timer` row. Expanding or collapsing lyrics must **never** shift the Play/Pause row vertically.
- **Circular Arc Seekbar (`CircularArcPlayerArt`)**: Seek gestures are restricted to the circular progress arc ring; dragging the album art image itself does not seek.
- **Lyrics Controls**: Offset `-`/`+`, Translate (`文A` tap to toggle, long-press to pick language via `GlassDialog`), Delete (with `GlassDialog` confirmation), Import `.lrc`, and Gemini Transcribe (when lyrics are missing or requested).
