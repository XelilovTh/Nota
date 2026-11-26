# PROJECT_RULES.md

Bu fayl layihə üzərində işləyən bütün agentlər və developerlər üçün məcburidir. Həmçinin `AGENTS.md`, `docs/ARCHITECTURE.md` və `docs/DESIGN_SYSTEM.md` qeydlərinə baxın.

## Ümumi prinsiplər
- Yalnız tapşırığın əhatə etdiyi sahələri dəyişin; əlaqəsiz faylları və ya mövcud şərhləri səbəbsiz silməyin.
- **Dizayn token qaydası (MƏCBURİ):** Rənglər, tipografiya, formalar və ölçülər **yalnız** `app/src/main/java/com/xelilovth/nota/ui/theme/` paketində təyin olunur. Komponentlərdə və ekranlarda hard-coded `.dp`, `.sp` və ya `Color(...)` yazmaq **qadağandır**.
- **Liquid Glass üslubu:** Bütün kartlar, dialoqlar (təsdiq pəncərələri, dil seçimi, API açarı dialoqu) və alt panellər `GlassSurface` / `GlassDialog` istifadə etməlidir.
- **Bütün düymələr real funksiya daşımalıdır:** Hazır funksiyanı yerinə yetirməyən placeholder düymə buraxmayın.
- **Player & Lyrics qaydaları:**
  - `NowPlayingScreen` `NotaApp.kt` içində modal overlay kimi saxlanılır (`NavHost` route-una qaytarılmır).
  - Mahnı sözlərinin tərcüməsi Gemini API limitini xərcləməmək üçün yalnız ödənişsiz onlayn tərcümə zənciri (`Google Translate gtx` -> `Lingva` -> `MyMemory`) ilə aparılır; Gemini yalnız audio-dan söz çıxarma (`generateLyricsWithGemini`) üçün istifadə olunur.
  - `PlaybackPlayerHolder` üzərində `SeekParameters.EXACT` və `setConstantBitrateSeekingEnabled(true)` qorunur (`FLAG_ENABLE_INDEX_SEEKING` əlavə edilmir).

## Keyfiyyət qapıları
Hər dəyişiklikdən sonra bu ardıcıllıqla yoxlanır:
1. Token auditi: `rg -n "\.dp\b|\.sp\b|Color\(" app/src/main/java/com/xelilovth/nota/ --glob "!**/ui/theme/**"` (0 nəticə olmalıdır)
2. `./gradlew --no-daemon assembleDebug` — xəta olmamalıdır
3. `./gradlew --no-daemon lint` — kritik xətalar düzəldilir
4. `./gradlew --no-daemon testDebugUnitTest` — bütün testlər keçməlidir
5. Release build (CI): `./gradlew --no-daemon assembleRelease` + `apksigner verify`

Qapı xəta verərsə:
- Tam xəta logu götürülür, düzəldilir və qapı yenidən işlədilir (maksimum **3 cəhd**).
- Keçməyən kod `main`-ə merge olunmur.
- **Lokal mühitdə Android SDK yoxdursa**, token auditi lokal aparılır, build/lint/test doğrulaması isə **GitHub Actions CI** (`.github/workflows/android-build.yml`) üzərindən yoxlanır və bu hesabatda açıq qeyd edilir.

## Git və CI qaydaları
- Commit mesajı ingilis dilində, `type: short description` formatındadır (`feat:`, `fix:`, `refactor:`, `docs:`, `test:`, `chore:`).
- Push yalnız istifadəçi istədikdə və ya tapşırıq tələb etdikdə edilir (istifadəçi öz Git Bash terminalından `git push` edə bilər).
- `.github/workflows/android-build.yml` istənilən branch-a `push` və `pull_request` zamanı avtomatik işə düşür və `nota-debug-apk`, `nota-release-apk` (imzalı) və `nota-lint-test-reports` artifact-larını yaradır.
- Repo tarixçəsi silinmir və yenidən yazılmır (`git push --force` və `rebase` ilə tarixçə dəyişdirilmir).
- Kredensial, API açarı, keystore və ya token heç vaxt commit olunmur.

## İcazələr və Manifest
- API 33+: `READ_MEDIA_AUDIO`
- API 32 və aşağı: `READ_EXTERNAL_STORAGE` (`android:maxSdkVersion="32"`)
- `POST_NOTIFICATIONS` (API 33+, runtime sorğusu ilə)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (API 34+)
- `INTERNET` (onlayn söz axtarışı, Gemini audio transkripsiyası və tərcümə üçün)

## .gitignore (minimum)
```
build/
.gradle/
local.properties
*.jks
*.keystore
keystore.properties
.idea/
*.iml
```

## Hesabat formatı
- Dəyişdirilən və ya yaradılan faylların siyahısı
- Yoxlama / keyfiyyət qapılarının nəticəsi (lokal token auditi, CI və ya ADB QA)
- Commit hash (varsa) və push statusu
- Açıq məsələlər (varsa)
