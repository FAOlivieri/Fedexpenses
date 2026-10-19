# Fedexpenses: notes for working on this repo

Android expense tracker (Kotlin, Jetpack Compose, Room, WorkManager). Single module `app/`, package
`io.github.faolivieri.fedexpenses`. The README describes features and installation; this file is about
working on the code.

## Workflow

- New work goes on the `untested` branch. The owner tests it on their phone before anything reaches `main`.
- **Don't commit, push, merge or publish releases unless asked.** When asked to build for testing:
  bump the version, build the signed release APK, and (if the phone is connected) install it with
  `adb install -r`.
- The owner picks version numbers. Bump both `versionCode` (+1 every build that will be installed;
  Android refuses an update with a lower or equal code) and `versionName` in `app/build.gradle.kts`.
- Merges to `main` are fast-forward. Releases: `gh release create vX.Y.Z <apk> --target main`, with notes
  covering everything since the previous *published* release.
- Commit messages: `Version X.Y.Z: <summary>`, a blank line, then `-` bullets of user-visible changes.

## Build and test

Requires JDK 17 and the Android SDK (platform 35, build-tools 35).

```sh
./gradlew testDebugUnitTest   # JVM unit tests (fast; run them after every change)
./gradlew assembleRelease     # signed, R8-minified APK: Fedexpenses-<version>-release.apk
./gradlew assembleDebug       # debuggable build (allows run-as / sqlite3 inspection on a device)
```

- `local.properties` (git-ignored) holds `sdk.dir`, the `fedexpenses.*` signing entries and an optional
  `buildRoot` that moves build output elsewhere (useful inside OneDrive/Dropbox, which lock files
  mid-build). With `buildRoot` set, APKs are under `<buildRoot>/app/outputs/apk/`, not `app/build/`.
- Without signing entries, release builds fall back to the local debug key. Those can't update an
  install of the published app (different signature). Check with
  `apksigner verify --print-certs <apk>`; the release certificate SHA-256 starts with `b167c41881b6dd7a`.
- Judge scrolling and performance on **release** builds only; debug Compose builds stutter.
- Never commit `local.properties`, keystores (`*.jks`), or personal data exports (`Transactions *.csv`);
  `.gitignore` covers them. `sample-transactions.csv` is the fictional sample used by tests and docs.

### Unit tests

Pure logic lives in `data/` and `notify/` and is tested on the JVM: `Money`, `Csv`, `RuleEngine`
(notification patterns), `SellerMatcher`, `Recurrences`, `Splits`. Keep new logic there, free of Android
types, and add tests next to the existing ones in `app/src/test/`. `org.json`, `ColorUtils` and other
Android classes aren't available in JVM tests.

### Emulator / device testing

- Prefer testing upgrades the way users get them: install the previous **published** release
  (`gh release download`), add data (e.g. import `sample-transactions.csv`), then `adb install -r` the
  new APK and check that totals and account balances are unchanged.
- Release APKs aren't debuggable, so `run-as` / `sqlite3` only work with debug builds.
- With a phone and an emulator both connected, set `ANDROID_SERIAL` so commands can't hit the phone.
- Avoid gradient shaders (`Brush.*Gradient` in large areas, `Canvas` gradients): they crashed the
  emulator's software renderer. Plain colored blocks are used instead (e.g. the color picker's hue bar).

## Architecture

- `ExpensesApp` / `AppContainer`: manual DI, everything lazy so the notification listener starts cheaply.
- `data/`: Room entities and DAOs, `Repository` (all writes go through it), CSV and JSON backup,
  seller rules, repeating payments, splits.
- `notify/`: `PaymentListenerService` (a `NotificationListenerService`; the system keeps it bound, so
  there's no foreground service), `NotificationProcessor` (dedup, pattern matching, recording),
  `TxNotifier` (the app's own notifications and their action buttons).
- `work/`: hourly listener watchdog, daily backup, and the nightly alarm that creates repeating payments
  (also caught up whenever the app or listener starts).
- `ui/`: Compose screens and two ViewModels (`MainViewModel`, `SettingsViewModel`); theme in `ui/theme`.

## Rules that are easy to break

- **Database changes.** Bump `@Database(version)`, add a `Migration` to `AppDatabase.build`, and make its
  SQL match Room's generated schema exactly (compare with `app/schemas/.../<version>.json` →
  `createSql`; Room refuses to open a database whose schema differs). Then test an upgrade from the last
  published release with real data. A schema version that has been released must never change.
- **Backups.** When stored data changes, update `Backup.toJson`/`fromJson`, bump `Backup.VERSION`, and
  keep reading every older version (missing fields get defaults; see the legacy conversions there).
  `restoreBackup` clears and re-inserts tables in foreign-key order and unlinks captured notifications.
- **Notification patterns** are matched against `title + "\n" + text`; each pattern box holds one regex per
  line, first match wins, income patterns before expense patterns. `Defaults.GOOGLE_WALLET_PATTERN` is
  the shipped default; change it only with tests in `LogicTest`/`RuleDirectionsTest`.
- **Android 15+ hides OTP-like notifications** (e.g. a standalone 4-digit card number) from third-party
  listeners. That's detected (`hidden`) and the user is prompted to add the payment by hand; it can't be
  worked around.
- **Money** is `Long` cents everywhere; `Transaction.amountCents` is always positive and `type` gives the
  sign. Split parts are basis points (10000 = whole) and must add up to the total (`Splits.parts`).
- **Theme colors** are generated by luminance, not HSL lightness (`withLuminance` in `Theme.kt`), so any
  palette or custom color keeps text readable. When adding roles, give them luminance targets too, and
  check selected states (secondaryContainer) against tinted surfaces in light and dark mode.
- **Large fonts.** Labels that must stay on one line use `FitText` or shrink-to-fit logic; the period
  selector wraps to two lines when needed. Test with font scale 2.0 and the largest display size.

## Environment tips for Claude Code on Windows

- Shell heredocs collapse backslashes (`\\d` becomes `\d`); write files containing regexes or escapes
  with the file-writing tools, not `cat <<EOF`.
- In Command Prompt the wrapper is `gradlew`, not `./gradlew`; set `JAVA_HOME` first.
