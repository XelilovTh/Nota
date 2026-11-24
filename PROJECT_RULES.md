# PROJECT_RULES.md

Bu fayl bütün mərhələlərə aiddir. Hər mərhələ promptu bu qaydalara istinad edir və onları təkrar etmir.

## Ümumi prinsiplər
- Hər mərhələ yalnız öz tapşırıqlarını yerinə yetirir. Növbəti mərhələnin işi əvvəlcədən əlavə edilmir.
- Dizayn sonradan dəyişəcək: rənglər, tipografiya, formalar və ölçülər yalnız `ui/theme/` paketində təyin olunur. Komponentlərdə hard-coded rəng və ölçü olmur.
- Bütün düymələr real funksiyasını yerinə yetirməlidir. Yer tutan ekran yalnız açıq şəkildə "placeholder" kimi qeyd olunduqda istifadə olunur.
- Phase 1-də EQ, Sleep Timer, Lyrics və custom playlist məntiqi yazılmır.

## Keyfiyyət qapıları
Hər mərhələdə bu ardıcıllıqla yoxlanır:
1. `./gradlew assembleDebug`: xəta olmamalıdır
2. `./gradlew lint`: kritik xətalar düzəldilir, xəbərdarlıqlar düzəldilir və ya səbəbi yazılır
3. `./gradlew testDebugUnitTest`: bütün testlər keçməlidir

Qapı xəta verərsə:
- Tam xəta logu götürülür, düzəldilir və qapı yenidən işlədilir.
- Hər qapı üçün maksimum **3 cəhd**.
- 3-cü cəhddən sonra da keçmirsə, dayanılır və hesabat verilir: dəqiq xəta, nə cəhd edildiyi, ehtimal olunan səbəb.
- Keçməyən kod push olunmur.

Lokal Gradle və ya Android SDK yoxdursa, GitHub Actions build doğrulama kimi istifadə olunur və hesabatda bu aydın yazılır.

## Git qaydaları
- Commit yalnız bütün qapılar keçəndən sonra edilir.
- Commit mesajı ingilis dilində, `tip: qısa təsvir` formatındadır (məsələn, `feat: ...`, `chore: ...`, `test: ...`).
- Push yalnız mərhələ promptunda açıq göstərildikdə edilir.
- Push sonrası GitHub Actions run yoxlanılır. Xəta varsa, eyni 3 cəhd limiti ilə düzəldilir.
- Repo tarixçəsi silinmir, yenidən yazılmır (`force push` və `rebase` ilə tarixçə dəyişdirilmir).
- Kredensial təxmin olunmur. Token və ya açar commit olunmur.

## İcazələr və manifest
- API 33+: `READ_MEDIA_AUDIO`
- API 32 və aşağı: `READ_EXTERNAL_STORAGE`, `android:maxSdkVersion="32"`
- `POST_NOTIFICATIONS` (API 33+, runtime sorğusu ilə)
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (API 34+)
- Runtime icazə sorğusu yalnız scanner mərhələsində əlavə olunur.

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

## Dayanma şərtləri
Aşağıdakılardan biri olduqda dayanılır və hesabat verilir:
- Repo, Gradle və ya lazımi icazə/kredensial mövcud deyil
- Hər hansı qapı 3 cəhddən sonra keçmir
- Mövcud istifadəçi faylları və ya repo tarixçəsi silinməli olur

## Hesabat formatı
- Dəyişdirilən və ya yaradılan fayllar (qısa siyahı)
- Hər qapının nəticəsi: keçdi/keçmədi, cəhd sayı
- Commit hash (varsa) və push statusu
- Açıq məsələlər və fərziyyələr
- Növbəti mərhələ üçün tövsiyə
