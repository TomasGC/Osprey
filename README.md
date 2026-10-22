# Osprey

Photo and video library cleanup tool for desktop (Compose Multiplatform) and Android (Jetpack Compose).
It sorts a library into year folders, strips sync copy-suffix noise, finds exact and visually-identical
duplicates, fixes timestamps and standardizes file names. Scan first, review, then apply. Nothing is
deleted: unwanted files are moved to a `Trash` folder for you to clear out yourself.

## Documentation

Everything about what Osprey does and how it is built lives in the [wiki](https://github.com/TomasGC/Osprey/wiki):

- [Product Requirements](https://github.com/TomasGC/Osprey/wiki/Product-Requirements): the actions, the library layout, platform limits
- [Architecture](https://github.com/TomasGC/Osprey/wiki/Architecture): modules, platform interfaces, UI structure, CI
- [Roadmap](https://github.com/TomasGC/Osprey/wiki/Roadmap): what is done and what is next
- [Development Conventions](https://github.com/TomasGC/Osprey/wiki/Development-Conventions): commits, branches, tests, the local gate

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

## Tests

```
./gradlew :core:test :core:integrationMock :core:integrationReal \
  :desktop:test :desktop:integrationMock :desktop:integrationReal :app:testDebugUnitTest
```

## License

GPL-3.0, see [LICENSE](LICENSE).
