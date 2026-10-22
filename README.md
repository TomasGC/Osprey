# Osprey

Photo and video library cleanup tool, named for how the bird hunts: patient scanning before
picking the one true target out of clutter. Scan first, review, then apply. Nothing is deleted:
unwanted files are moved aside for you to clear out yourself.

Runs as a desktop app (Compose Multiplatform) and as an Android app (Jetpack Compose).

## Actions

Expects a library laid out as `<library root>/<year>/<files>`. Each action works on one year folder.

1. **Flatten**: moves files out of nested subfolders into the year folder; collisions get a `__flatN` suffix.
2. **Rename**: strips OS/sync duplicate-copy suffixes (`(2)`, `_1`, `- Copy`, `copy 2`) when safe.
3. **Dedupe**: groups files by size then content hash; exact duplicates move to `TODELETE/`.
4. **Visual Dupes**: perceptual-hash near-duplicates (same picture, different bytes), images and video.
   Burst-mode frames are never treated as duplicates of each other. You review each group and
   the ones you don't want move to `TODELETEDUPS/`.
5. **Dates**: sets file timestamps from EXIF (images), video metadata, or a date parsed from the filename.
6. **Standardize**: renames to `IMG_YYYY-MM-DD_HH-MM-SS` / `VID_...` from the resolved capture date.

## Requirements

- JDK 17 (Gradle toolchain; the wrapper pins Gradle 8.9)
- Desktop: `ffmpeg` and `ffprobe` on `PATH`, used to read video dates and extract video frames.
  When missing, video dates and video frames are skipped instead of failing the run.
- Android: Android SDK (compileSdk 36) and a device or emulator on API 26+

## Build and run

```
./gradlew :desktop:run          # desktop app
./gradlew :app:installDebug     # Android app on a connected device or emulator
./gradlew build detekt          # build + static analysis
```

Desktop remembers the library folder in `~/.osprey/config.json`. Android asks for it through the
system folder picker on first launch.

## Modules

| Module | Contents |
|--------|----------|
| `:core` | Platform-independent logic: the six planners, filesystem and media interfaces |
| `:desktop` | Compose Desktop UI, `java.nio` filesystem, metadata-extractor and ffmpeg/ffprobe |
| `:app` | Android UI, Storage Access Framework filesystem and media reading |

## Platform notes

- Dates sets the Created time on Windows only; elsewhere on desktop it sets the modified time.
- Android cannot set file modified times through the Storage Access Framework, so Dates is
  scan-only there (Apply disabled). Tracked in #29.

## Tests

```
./gradlew :core:test :core:integrationMock :core:integrationReal \
  :desktop:test :desktop:integrationMock :desktop:integrationReal :app:testDebugUnitTest
```

## Origin

Osprey was first prototyped in Python as a throwaway proof of concept. That prototype was never
published; this repository is the Kotlin rewrite, and the Python behavior served as its spec.

## License

GPL-3.0, see [LICENSE](LICENSE).
