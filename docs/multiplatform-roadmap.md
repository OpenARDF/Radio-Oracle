# Radio-Oracle Multiplatform Roadmap

This roadmap tracks the move from an Android-only app toward a shared Kotlin
foundation with a desktop beta. The first desktop target is an event-admin MVP,
not full Android parity.

## Current foundation

The `codex/multiplatform-foundation` branch introduces a `:shared` Kotlin
Multiplatform module with Android and desktop JVM targets. Shared code currently
covers portable domain enums, SportIdent code and time helpers, duration
formatting, file definitions, alias validation, control-point parsing, import
validation, result ranking, course evaluation, IOF result-status mapping,
platform-neutral event models, event validation, result placement, project-file
envelope metadata, CSV row formatting, control/punch display formatting,
template rendering, standard-category row parsing and presets, network endpoint
definitions, and result-send filtering.

Android remains the production app. The Android Room database, UI, USB
SportIdent readout, printing, live result sending, Android resources, and
platform permissions still live in the Android app. Android now has tested
mappers between Room aggregates and shared event models, but Room remains the
Android persistence layer.

## Required gates

Run this gate before committing roadmap/foundation changes:

```shell
./gradlew :shared:check testDebugUnitTest :shared:desktopSmokeRun :desktopApp:test
git diff --check
```

`desktopSmokeRun` is a shared-module desktop JVM smoke entrypoint. It proves the
desktop target can execute shared business logic. The `:desktopApp:test` task
checks the first launchable desktop UI module without opening a window.

When validating local desktop packaging work, use a registered JDK 17 and run:

```shell
./gradlew :desktopApp:checkRuntime :desktopApp:createDistributable
```

On macOS, Temurin 17 is the recommended registered JDK. The current
`createDistributable` output is an app image under
`desktopApp/build/compose/binaries/main/app/`.

Known Room/KSP warnings about missing indexes and `@Transaction` annotations
currently predate this roadmap work. Do not treat those warnings as Stage 1
blockers unless a change in this branch introduces new failures.

## Standards compatibility

Radio-Oracle must not intentionally drift farther from
[`radio-o-standards`](https://github.com/AROB-CR/radio-o-standards) without a
prior pull request to the standards repository. Follow
[`standards-compatibility-policy.md`](standards-compatibility-policy.md) before
changing ARDF JSON, ARDF XML, IOF mapping, import/export semantics, or
standards-facing event data shapes. The same policy is a required pre-deployment
inspection gate for every Android and desktop release candidate.

## Desktop beta goal

The first desktop release should be a beta event-admin app. It should support:

- creating, opening, editing, saving, and exporting event data;
- managing races, categories, control points, aliases, competitors, readouts,
  and results;
- downloading SI5/SI6/SI8/SI9/SIAC cards from an attached SPORTident station
  that is already configured in READOUT/SI MASTER mode, first as a one-card
  action and then through the experimental continuous-readout loop;
- previewing finish-ticket text and sending unsent matched ROBIS live results
  through the shared result payload path;
- importing and exporting the supported event/result formats once the shared
  import/export layer is ready.

The desktop beta should explicitly exclude:

- hardened Android-style SportIdent race-day reader replacement;
- ticket printing;
- additional live-result providers beyond the first ROBIS path;
- replacing Android for normal race-day readout operations.

## ARDFEvent reference benchmark

ARDFEvent is a useful desktop reference because it is a verified race-day
desktop application for the same problem space. It should inform the long-term
desktop feature set, but it should not pull Radio-Oracle away from the current
shared Kotlin architecture or Android-compatible data model.

Relevant ARDFEvent feature areas to keep in view:

- persistent race selection backed by one local SQLite database per event;
- dense desktop navigation with task-specific pages for event info, controls,
  categories, import, competitors, SI readout, start lists, results, runners in
  forest, and advanced repair tools;
- control presets, mandatory/spectator control flags, coordinates, and
  category course assignment;
- competitor autocomplete/import from registration data, club lookup, start
  numbers, start times, and manual DNS/DSQ flags;
- start-list drawing that separates categories and clubs, plus start-list
  exports by category, by minute, ROBIS CSV/JSON, and IOF XML;
- continuous SI readout with duplicate-card overwrite handling, unmatched-card
  assignment, readout status window, error sound, and immediate result refresh;
- ESC/POS ticket printing for competitor tickets and string tickets, including
  printer setup, test printing, optional QR/link, and double-print support;
- result calculation from stored punches with DNS, DSQ, DNF, MP, OVT, and
  running/unknown states, with places sorted by transmitter count and time;
- in-forest tracking from start times and readout state, including last-limit
  visibility for operators;
- result exports to HTML, HTML with splits, CSV, IOF XML, and ARDF JSON;
- a local finish-line web server exposing live category/result JSON plus public
  and organizer static views;
- OCheckList import and a signed plugin system with startup, readout, and menu
  hooks.

For Radio-Oracle, this suggests the next desktop parity work should prioritize
operator workflow gaps before optional extensibility: start-list management,
in-forest tracking, full ticket printer transport, duplicate/unmatched readout
handling polish, richer status/error surfaces, and export coverage. A plugin
system should remain long-term unless a concrete integration cannot be handled
through shared services or ordinary platform UI.

The concrete desktop boundary, storage approach, UI direction, and packaging
default are tracked in [`desktop-prep.md`](desktop-prep.md).

## Stages

### 1. Foundation stabilization

Goal: make the existing shared-module foundation easy to review, test, and hand
off.

- Keep the branch focused on shared extraction and documentation.
- Preserve Android behavior while shared code grows behind tested adapters.
- Document the shared module purpose, desktop smoke task, test gates, and desktop
  limitations.
- Merge only after the required gates pass.

Milestone: the branch is clean, pushed, documented, and another engineer can run
the shared/Android verification commands without extra context.

### 2. Shared event model and services

Goal: move event-domain behavior behind platform-neutral models and services.

- Add shared models for race, category, competitor, alias, control point, result,
  punch, and aggregate race data.
- Keep Android Room entities as platform persistence objects and map them to/from
  shared models.
- Move category validation, competitor validation, alias/control-point
  management, readout/result recalculation, result grouping, and place assignment
  into shared services.
- Use test-driven slices: write shared common tests first, then delegate Android
  code.

Milestone: Android still uses Room, but the core event workflows are expressed
and tested in shared code.

### 3. Shared import/export layer

Goal: make file formats portable while leaving file pickers and permissions on
each platform.

- Move CSV parsing/writing policy, Radio-Oracle JSON shaping, final/live
  result JSON shaping, and IOF XML policy into shared code where platform-neutral.
- Define shared import/export APIs that accept text/bytes plus shared event data
  and return data or structured validation errors.
- Keep Android streams, content URIs, localized resources, and platform UI in the
  Android app.
- Add golden-file tests for CSV, JSON, and XML.

Milestone: Android import/export behavior remains compatible, and desktop can
reuse the same format code.

### 4. Desktop event-admin MVP

Goal: build the first user-visible desktop app around shared services.

- Add a thin desktop app module or desktop source set, following the shared JVM
  smoke pattern already in place.
- Use file-backed project storage for beta, such as a `.rom.json` event file.
- Build desktop workflows for race selection, category/control-point/alias
  editing, competitor editing, readout/result entry, result recalculation, and
  import/export.

Milestone: a desktop user can create/import an event, edit event data, manually
enter readout-equivalent punch data, recalculate results, save/reopen, and export
results.

### 5. Desktop beta packaging

Goal: produce installable desktop beta artifacts.

- Add version/build metadata for desktop artifacts.
- Keep Android, npm/jDeploy, and native desktop package versions aligned from
  `1.0.0` onward. Future releases increment only the third, rightmost field.
- Use jDeploy as the default packaging path unless a focused packaging spike
  finds a concrete blocker; keep Conveyor as a comparison option and `jpackage`
  as a low-level fallback.
- Before treating USB SportIdent download as safely post-beta, run a focused
  desktop USB feasibility spike with the actual SPORTident USB download box.
  The spike only needs to prove that a packaged desktop app can discover the
  device, open the serial port, exchange a small SI protocol probe, and close
  cleanly on macOS at minimum.
- Add packaging environment checks and package tasks for macOS, Windows, and
  Linux.
- Document beta limitations clearly.
- Verify packaged apps launch and complete the event-admin smoke scenario.

Milestone: beta desktop packages are produced and validated on target desktop
operating systems, with no known desktop USB showstopper left uninvestigated.

### 6. Post-beta platform features

Goal: add platform-specific capabilities after the event-admin beta is stable.

- Harden the first desktop continuous SportIdent readout loop into a race-day
  reader workflow behind a platform device interface.
- Reuse the shared finish-ticket renderer when adding desktop printer transport
  so Android and desktop ticket text stay aligned.
- Add a read-only Station Maintenance surface for attached SportIdent stations.
  It should show station serial number, reported function/mode, code number,
  firmware/config metadata when available, protocol flags, and explicit
  warnings when a download box is not in READOUT/SI MASTER mode.
- Add Station Maintenance diagnostics for attached download stations, including
  a response-timing test and a settings comparison test across known-good and
  suspect units. If a station is in the correct READOUT/SI MASTER mode but is
  still sluggish or has unexplained configuration/status differences, recommend
  resetting it to factory defaults and then reapplying the desired event
  settings before using it for race-day downloads. These diagnostics must be
  advisory: log and report timing/configuration concerns, but continue to allow
  downloads whenever the station can still read cards. Only hard-block downloads
  when the station cannot be opened, cannot answer the protocol, or reports a
  clearly non-download mode.
- Extend Station Maintenance to read coupled non-reader stations through the
  USB master/download station when the remote/coupled-station protocol is
  verified. This should be read-only first and should report basic station
  information such as serial number, mode/code, clock/status fields, operating
  time, battery-level or battery-status fields, and backup-memory/status flags
  where the protocol exposes them.
- Treat station writes as a later, guarded maintenance phase. A "set attached
  download box to READOUT" action may be added only after the SPORTident
  configuration write transaction is verified against real hardware and has
  immediate read-back validation. The UI must warn that applying station
  settings can overwrite station configuration and may clear backup data, and
  it must refuse to report success unless the station re-reads as READOUT.
- Add desktop printing behind a platform print interface.
- Add non-ROBis live result providers after their network/result-service logic
  is isolated from Android WorkManager.

Milestone: each platform feature lands behind shared tests plus platform smoke
tests without regressing Android.

## Shared SQL decision

Shared SQL is not on the critical path for the first desktop beta.

Use file-backed project storage for the desktop beta while shared domain models,
services, and import/export APIs stabilize. After the beta, run a bounded shared
SQL spike with Room KMP as the baseline candidate. SQLDelight remains the
fallback/comparison option if Room KMP limitations are unacceptable.

Reasons:

- The current Android app already has a Room schema, DAOs, relations, migrations,
  flows, and transactions.
- Moving persistence into shared SQL now would be a storage migration, not a
  small adapter change.
- The desktop beta primarily needs reliable event files and shared business
  logic.
- Deferring shared SQL reduces risk and gets a useful desktop package in users'
  hands sooner.

## Desktop beta decisions

The desktop beta should continue to advance conservative event-admin and
loopback-display slices while these boundaries remain in force:

- Local web display stays bound to loopback until LAN exposure is hardened and
  explicitly selected.
- Desktop printer transport starts with system printing. The current known
  target is the WiFi Epson printer visible as `EPSON ET-2720 Series`; Bluetooth
  printer support remains a separate adapter after the system-printer path is
  validated.
- OCheckList/new-card import waits for a verified sample file or schema.
- Shared SQL waits until after the file-backed desktop beta.
- SPORTident station writes wait for verified write/read-back transactions on
  real hardware.

## Acceptance criteria for Stage 1

- `docs/multiplatform-roadmap.md` exists and describes the staged roadmap.
- README links to this roadmap.
- The branch passes:

```shell
./gradlew :shared:check testDebugUnitTest :shared:desktopSmokeRun :desktopApp:test
git diff --check
```

- The branch is committed and pushed to `origin/codex/multiplatform-foundation`.
