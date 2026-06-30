# Radio-Oracle Multiplatform Roadmap

Status reviewed: 2026-06-30.

Radio-Oracle is no longer an Android-only app with a hypothetical desktop beta.
It is a shared Kotlin project with Android race-day workflows, a desktop Event
File workflow, desktop packaging through jDeploy, and growing shared event,
import/export, Course Analyzer, Event Series, and validation services. This
roadmap now tracks the work that remains after the initial multiplatform
foundation and desktop event-admin milestones.

## Current State

The `:shared` Kotlin Multiplatform module is the core portability boundary. It
contains platform-neutral event models, domain enums, SPORTident code and time
helpers, duration and control/punch formatting, file definitions, alias and
control parsing, import validation, result ranking, course evaluation, event
validation, result placement, Event File envelope metadata, CSV row formatting,
template rendering, standard-category parsing and presets, network endpoint
definitions, result-send filtering, Event Series support, and many import/export
helpers.

Android remains the mature race-day platform for USB SPORTident readout,
Bluetooth printing, Android-specific UI, Android Room persistence, Android
resources, and platform permissions. Android has tested mappers between Room
aggregates and shared event models, but Room remains the Android persistence
layer.

Desktop is now an active event-admin and analysis platform. The desktop app uses
file-backed `.rom.json` Event Files, exposes setup/race/results workflows, reads
SPORTident cards from attached READOUT/SI MASTER stations, supports desktop
system printing for finish-ticket text, sends ROBIS live results, provides local
and public result-site workflows, packages through jDeploy, and includes Course
Analyzer, Event Validator, Event Series, and testing tools.

## Validation Gates

Prefer the repo wrappers for routine local validation:

```shell
just test
just desktop-check
just desktop-package
git diff --check
```

Use focused recipes when the change affects those surfaces:

```shell
just android-test
just android-compile
just jdeploy-preflight
just jdeploy-smoke
```

The lower-level Gradle tasks still matter when diagnosing failures or validating
a specific layer:

```shell
./gradlew :shared:check testDebugUnitTest :shared:desktopSmokeRun :desktopApp:test
./gradlew :desktopApp:checkRuntime :desktopApp:createDistributable :desktopApp:verifyDesktopDistributable
./gradlew :desktopApp:prepareDesktopJdeployBundle :desktopApp:verifyDesktopJdeployBundle
```

For full jDeploy releases, use the release process documented in
[`desktop-prep.md`](desktop-prep.md). The public user-facing install path is the
jDeploy GitHub release page linked from the README.

## Standards Compatibility

Radio-Oracle must not intentionally drift farther from
[`radio-o-standards`](https://github.com/AROB-CR/radio-o-standards) without a
prior pull request to the standards repository. Follow
[`standards-compatibility-policy.md`](standards-compatibility-policy.md) before
changing ARDF JSON, ARDF XML, IOF mapping, import/export semantics, or
standards-facing event data shapes. The same policy is a required pre-deployment
inspection gate for every Android and desktop release candidate.

## Completed Milestones

These items were roadmap goals earlier, but are now implemented enough that they
should be treated as current project foundation rather than future work.

### Shared Foundation

- Shared event models and tested Android mapper paths exist.
- Core event validation, result placement, ranking, course evaluation, category
  and control assignment policies, competitor identity fields, and many display
  helpers are shared.
- CSV, TXT, HTML, IOF XML, ARDF JSON-facing policy, ROBIS request metadata, and
  selected import/export paths have shared implementations and tests.
- The desktop smoke target and desktop app test suite are part of the normal
  verification surface.

### Desktop Event-Admin App

- Desktop can create, open, edit, save, and export Event Files.
- Desktop setup workflows cover event settings, categories, controls,
  competitors, start lists, readouts, results, imports, exports, and live result
  settings.
- Desktop can read SPORTident card downloads from attached READOUT/SI MASTER
  stations, including continuous readout behavior, while Android remains the more
  mature race-day reader.
- Desktop finish-ticket text uses the shared ticket renderer and can be sent to
  desktop system printing.
- Desktop ROBIS live-result sending exists, including background sending
  settings.

### Packaging And Deployment

- jDeploy is the selected public install path.
- GitHub-release jDeploy publication and npm/Trusted Publishing workflows exist.
- Package-preflight, local-smoke, release-preflight, and registry-smoke scripts
  are documented in `desktop-prep.md`.
- Android, desktop, and npm/jDeploy version alignment is part of the release
  process.

### ARDFEvent Compatibility Work

- The canonical Radio-Oracle competitor CSV remains the primary round-trip CSV
  format.
- ARDFEvent-compatible registration CSV import is supported as an alternate
  profile.
- Desktop ROBIS start-list CSV export is available without changing the
  canonical Radio-Oracle start-list CSV.
- Desktop ARDFEvent-style results CSV export is available.
- Result exports also include TXT, HTML, IOF XML, and related shared export
  paths.

### Course Analyzer

- Course Analyzer evaluates saved and imported route data, calculates ideal
  route candidates, handles effective length when elevation data is available,
  applies USA rules checks, reports wait-time renumbering, exports PDF/KML, and
  documents current limitations.
- Classic route search is exhaustive within the current control-count limits.
  Sprint loops are optimized separately with bounded permutations. Larger
  Foxoring routes use a documented non-exhaustive hybrid heuristic.
- KML/KMZ and GPX course import paths support protected control locations, route
  geometry, route assumptions, circular LineString filtering, and per-leg
  `SS=#.##` speed factors.

### Event Series And Start Fairness

- Event Series manifests group existing Event Files without duplicating core
  event data.
- Desktop Event Series workflows support validation, clean export, competitor
  matching reports, start-fairness summaries, and start-fairness optimization.
- Android can store, list, import, export, and transfer Event Series packages.
- The shared balanced-thirds start-list engine is used by series-aware start
  balancing tools.

## Active Boundaries

These are deliberate limits in the current app, not necessarily defects.

- Android remains the primary race-day platform for mature USB readout and
  Bluetooth ESC/POS printing.
- Desktop SPORTident support is useful, but additional station diagnostics,
  multi-station coordination, and configuration writes require more hardware
  validation.
- Desktop Bluetooth printer transport remains a separate future adapter. Desktop
  printing currently uses system printing.
- Local results web server exposure should stay loopback/local unless LAN
  exposure is explicitly hardened and selected.
- OCheckList/new-card import remains future work until sample files or schema
  details are available.
- Shared SQL remains deferred; desktop Event Files are still the right storage
  model for the current desktop app.
- Course Analyzer still lacks map passability knowledge. It does not know
  out-of-bounds areas, dense vegetation, lakes, uncrossable creeks/rivers,
  cliffs, fences, walls, or other barriers unless those effects are approximated
  by imported route geometry or speed factors.

## Medium-Term Roadmap

### Shared Event Services

- Continue extracting platform-neutral result recalculation glue from Android
  `ResultsProcessor` into shared services where it can be tested once and reused
  by Android, desktop, and Event Series tools.
- Keep competitor identity semantics shared. SI number, bib number, call sign,
  Person ID, full-name formatting, and cross-event matching keys must not drift
  between Android, desktop, and series workflows.
- Move durable Android table ordering helpers toward shared comparators when the
  ordering reflects domain policy rather than Android-only UI behavior.
- Gradually remove remaining Android compatibility alias facades in small,
  compile-proven stages.
- Continue auditing Android legacy import/export processors against shared
  `TextResultExports`, `HtmlResultExports`, `IofXmlExports`, and CSV paths so
  desktop and Android semantics do not diverge.

### Event Validation And Error Recovery

- Keep expanding Event Validator coverage for setup consistency, import mistakes,
  category/control mismatches, unused controls, missing SI numbers, duplicate
  labels/codes, suspicious category assignments, and late-workflow edits.
- Use validator logic from Series validation so each member Event File can be
  checked independently before cross-event checks run.
- Prefer source-of-truth repairs over display-only fixes: Setup > Controls owns
  SI-code-to-public-label mapping, Setup > Categories owns assigned controls,
  and downloaded SI readouts remain definitive evidence of visited station
  codes.

### SPORTident And Hardware

- Harden the desktop continuous SPORTident readout loop into a race-day reader
  workflow behind a platform device interface.
- Add a read-only Station Maintenance surface for attached SPORTident stations.
  It should show station serial number, reported function/mode, code number,
  firmware/config metadata when available, protocol flags, and explicit warnings
  when a download box is not in READOUT/SI MASTER mode.
- Add Station Maintenance diagnostics for attached download stations, including
  response-timing tests and settings comparison tests across known-good and
  suspect units. Diagnostics should warn and log when possible, but only
  hard-block downloads when the station cannot be opened, cannot answer the
  protocol, or reports a clearly non-download mode.
- Extend Station Maintenance to read coupled non-reader stations through a USB
  master/download station after the remote/coupled-station protocol is verified.
- Treat station writes as a later guarded maintenance phase. A "set attached
  download box to READOUT" action may be added only after the SPORTident
  configuration write transaction is verified against real hardware and has
  immediate read-back validation.
- Add explicit multi-download-station support so desktop can detect multiple
  connected stations, show their serial numbers/modes/ports, let the user choose
  or assign active stations, and prevent independent readout loops from fighting
  over the same serial device.
- Add a batch readout time-correction tool for common station clock mistakes.
  It should preview affected punches, apply signed offsets only to explicitly
  selected control punches, preserve an auditable before/after trail, and then
  recompute status, score, splits, places, exports, and sent/unsent state.

### Printing And Live Results

- Keep Android Bluetooth ESC/POS printing validated against target hardware.
- Add desktop printer transport abstractions only when system printing is not
  sufficient for a needed race-day workflow.
- Add non-ROBIS live-result providers after their network/result-service logic is
  isolated from Android WorkManager and represented through shared provider
  interfaces.
- Harden LAN/public result display choices so operators can distinguish local
  preview, loopback server, LAN exposure, generated public site, and Cloudflare
  publication.

### Competition And Series

- Keep current single-event workflows as the default. Series and championship
  tools should remain opt-in and additive.
- Move desktop-only Event Series reporting and optimization helpers into shared
  code as they stabilize.
- Extend Event Series with scoring and eligibility rules for championship
  standings.
- Add explicit cross-event competitor identity and reconciliation support for
  cases where SI numbers, start numbers, categories, or registration details are
  incomplete or change across days.
- Add competition scoring calculations for overall standings, with configurable
  point/placement rules, category scope, absent-result handling, eligibility, and
  tie-break behavior.
- Add championship exports for overall standings, per-event contributions, and
  start-slot fairness traces as derived outputs over linked Event Files plus
  lightweight series metadata.
- Add a Competition View only after the underlying series metadata,
  reconciliation, scoring, and export behavior is stable.

### Course Analyzer And Route Intelligence

- Add map-informed Course Analyzer modeling after the app can ingest or
  reference course-relevant map data. Future timing should combine category
  factors, event-wide speed factor, per-leg `SS=#.##` factors, elevation,
  vegetation, runnability, barriers, water, out-of-bounds constraints, and other
  map-derived impediments.
- Preserve the current category speed-factor table as a provisional input, not a
  final source of truth.
- Extract a shared course-route optimization core after the current analyzer and
  generator route choices have characterization tests. The first shared layer
  should be pure route-ordering and shortest-effective-path logic; analyzer
  report context, imported-route comparison, wait timing, and fox renumbering
  should remain layered on top.
- Continue improving analyzer import UX so saved, imported, calculated, and
  unsaved analyzer data are always clearly distinguished.

### Event Editing Model

- Add autosave plus transaction/undo as a medium-term workflow improvement.
- Ordinary single-step edits should eventually autosave immediately and create a
  one-step undo checkpoint.
- Multi-step tools and bulk actions, including Course Analyzer, course imports,
  test-data insertion, calculated-route saves, and fox renumbering, should run
  inside explicit Event File transactions: stage all intermediate changes, then
  either discard the whole transaction on exit or commit and autosave it as one
  atomic change.
- Undo after a committed transaction should revert the whole transaction, not its
  internal substeps.

### Storage

Shared SQL is not on the critical path. Keep file-backed Event File storage for
desktop while shared domain models, services, and import/export APIs stabilize.
After the desktop file workflow and shared services are stable, run a bounded
shared SQL spike with Room KMP as the baseline candidate. SQLDelight remains the
fallback/comparison option if Room KMP limitations are unacceptable.

Reasons:

- Android already has a mature Room schema, DAOs, relations, migrations, flows,
  and transactions.
- Moving persistence into shared SQL is a storage migration, not a small adapter
  change.
- Desktop Event Files remain useful for transfer, review, testing, and series
  packaging even if shared SQL is added later.

## Acceptance Criteria For Future Work

- Feature work lands behind shared tests when the behavior is platform-neutral.
- Platform-specific behavior lands behind platform smoke tests or documented
  manual hardware validation when automation is not practical.
- Android behavior does not regress when shared code grows.
- Desktop Event File compatibility is preserved across release versions.
- Release candidates pass the relevant `just` wrappers, standards inspection,
  packaging checks, and jDeploy release gates for the surfaces they affect.
