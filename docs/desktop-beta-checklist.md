# Desktop Beta Checklist

Use this checklist before cutting a desktop beta build. macOS hardware
validation and Windows packaged-app smoke validation are release blockers. Linux
packaging is best-effort for this beta.

## Functional Acceptance

- Create, open, edit, save, close, reopen, and export `.rom.json` Event Files.
- Manage races, categories, control points, aliases, competitors, readouts,
  start lists, in-forest state, and results from the desktop UI.
- Import canonical competitor CSV and ARDFEvent registration CSV, including
  category-less competitors and placeholder-category warnings.
- Export canonical event CSV, ROBIS start-list CSV, ARDFEvent-style results
  CSV, ARDF JSON, Android race-backup JSON, live/final result JSON, IOF XML,
  TXT, and HTML outputs.
- Download multiple SI cards from a READOUT/SI MASTER station. Station
  diagnostics may warn or log, but must not block usable card downloads.
- Assign unmatched readouts, handle duplicates according to the selected
  policy, mark DNS, and recalculate results.
- Preview finish-ticket text and print through desktop system printing.
- Manually send ROBIS live results and verify background sending can be enabled
  and disabled visibly.
- Serve loopback-only local result/category/start/in-forest pages and JSON
  endpoints from the open desktop Event File.

## Release Gates

- `./gradlew :shared:check testDebugUnitTest :shared:desktopSmokeRun :desktopApp:test`
- `git diff --check`
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :desktopApp:checkRuntime`
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :desktopApp:createDistributable`
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :desktopApp:verifyDesktopDistributable`
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :desktopApp:prepareDesktopJdeployBundle :desktopApp:verifyDesktopJdeployBundle`
- Confirm `verifyDesktopJdeployBundle` verifies all required Compose/Skiko
  native runtime jars for Windows, Linux, and macOS on both Intel x64 and
  ARM64.
- `npm install`
- `npm run jdeploy:pack-preview`
- `npm run jdeploy:local-smoke`
- `npm run jdeploy:release-preflight`

## Manual Platform Checks

- macOS: packaged app opens, reads the attached SI download box, reports station
  warnings without blocking downloads, and prints a finish ticket through the
  selected system printer.
- Windows: packaged app opens, loads a sample Event File, exports CSV/JSON/XML
  files, and runs the desktop smoke workflow on x64 Intel hardware and, when
  available, ARM64 hardware.
- Linux: packaged app opens on x64 Intel hardware and, when available, ARM64
  hardware. Verify at least app launch and sample Event File loading before
  calling Linux support validated.
- Android regression: run the Android unit gate and smoke import/export/readout
  basics so desktop-shared changes do not regress the production baseline.

## Explicit Beta Exclusions

- Desktop Bluetooth printer transport.
- Android Room/shared SQL migration.
- SPORTident station write/reprogramming actions.
- Multi-download-station support. Beta downloads may use one attached
  SPORTident download station, not multiple connected stations at once.
- OCheckList/new-card import without a verified schema or sample.
- LAN-exposed local result displays.
- A promise that desktop replaces Android for normal race-day downloads.
