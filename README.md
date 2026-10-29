# ПРОБКА — Найди выход

Native Android puzzle prototype written in Kotlin. The initial build focuses on a small, offline-playable core.

## Current prototype features
- 6×6 traffic-jam puzzle board.
- Drag cars along their orientation.
- Move counter, reset, three starter layouts, level progression and local best score.
- Custom Canvas rendering; no third-party runtime dependencies.
- GitHub Actions workflow builds a debug APK and uploads it as `probka-debug-apk`.

## Build APK in GitHub Actions
1. Upload/push this project to a GitHub repository.
2. Open **Actions** and run **Build Android APK** (or push to `main`/`master`).
3. Open the successful workflow run and download artifact `probka-debug-apk`.
4. Unzip the artifact and install `app-debug.apk` on your Android device. Android may ask you to allow installation from the browser/file manager.

## Local build
Requires JDK 17, Android SDK platform 35 and Gradle 8.9:
```bash
gradle assembleDebug
```

## Important status
The source project is prepared, but this archive itself has not been compiled or device-tested in this environment. The GitHub Actions run is the build verification step.

## Product roadmap
1. Validate puzzle levels and touch interaction on real devices.
2. Expand to 100+ curated solvable levels and add accessibility/localization.
3. Add privacy-conscious analytics and consent handling.
4. Integrate rewarded ads and remove-ads purchase only after policy and store setup.
5. Prepare Play Store listing, privacy policy, screenshots, app signing and release bundle.