# Radio-Oracle Multiplatform Roadmap

Status reviewed: 2026-07-10.

Radio-Oracle is no longer an Android-only app with a hypothetical desktop beta.
It is a shared Kotlin project with Android race-day workflows, a desktop Race
File workflow, desktop packaging through jDeploy, and growing shared race,
import/export, Course Analyzer, Race Series, and validation services. This
roadmap now tracks the work that remains after the initial multiplatform
foundation and desktop race-admin milestones.

## Current State

The `:shared` Kotlin Multiplatform module is the core portability boundary. It
contains platform-neutral race models, domain enums, SPORTident code and time
helpers, duration and control/punch formatting, file definitions, alias and
control parsing, import validation, result ranking, course evaluation, race
validation, result placement, Race File envelope metadata, CSV row formatting,
template rendering, standard-category parsing and presets, network endpoint
definitions, result-send filtering, Race Series support, and many import/export
helpers.

Android remains the mature race-day platform for USB SPORTident readout,
Bluetooth printing, Android-specific UI, Android Room persistence, Android
resources, and platform permissions. Android has tested mappers between Room
aggregates and shared race models, but Room remains the Android persistence
layer.

Desktop is now an active race-admin and analysis platform. The desktop app uses
file-backed `.rom.json` Race Files, exposes setup/race/results workflows, reads
SPORTident cards from attached READOUT/SI MASTER stations, supports desktop
system printing for finish-ticket text, sends ROBIS live results, provides local
and public result-site workflows, packages through jDeploy, and includes Course
Analyzer, Race Validator, Race Series, and testing tools.

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
standards-facing race data shapes. The same policy is a required pre-deployment
inspection gate for every Android and desktop release candidate.

### IOF XML 3.0 And Radio Orienteering Extensions

IOF XML 3.0 remains the standards target for orienteering interchange. Keep IOF
core elements schema-valid and semantically plain: starts, finishes, ordinary
controls, course sequences, control positions, lengths, climbs, start lists,
entry lists, and result lists should use the IOF fields defined by the IOF 3.0
schema.

Radio Orienteering concepts that IOF XML does not natively model must not be
encoded by inventing IOF-core elements or overloading IOF fields with unrelated
meaning. This includes finish beacons, spectator beacons, transmitter-specific
roles, exclusion zones around start/finish/transmitters, power levels, antenna
polarization, frequencies, modulation types, transmitter schedules, and similar
radio-specific data.

Use IOF `Extensions` with a Radio Orienteering namespace for those fields. The
planned namespace is:

```xml
xmlns:ro="https://openardf.org/xml/radioorienteering/iof-extensions/1.0"
```

The `openardf.org` domain is used because it is controlled by the project owner,
while the namespace path names the sport vocabulary rather than the OpenARDF
application. `radioorienteering.org` is not project-controlled, and informal
labels such as `radio-o` should be reserved for prose or local shorthand rather
than the namespace identity. The XML prefix should normally be `ro`; the URI is
the stable namespace identifier.

Place extensions on the nearest IOF element that owns the concept:

- `Control/Extensions` for transmitter role, finish beacon, spectator beacon,
  frequency, modulation, polarization, power, schedule, and control-local
  exclusion zones.
- `CourseControl/Extensions` for course-specific requirements for a particular
  appearance of a transmitter/control.
- `Course/Extensions` for course-wide Radio Orienteering rules.
- `RaceCourseData/Extensions` or race-level `Extensions` for race-wide radio
  rules and defaults.

Imports should validate the IOF document first, parse supported IOF core data,
then parse recognized `ro:*` extension elements. Unrecognized but schema-valid
IOF content and unrecognized Radio Orienteering extensions should be shown in
the import preview as unsupported/preserved data rather than silently discarded.
Exports should always remain valid IOF XML 3.0 documents, with Radio
Orienteering extension data treated as optional enhancement data rather than the
primary full-fidelity Radio-Oracle exchange format. Use Race Files for
lossless Radio-Oracle-to-Radio-Oracle interchange.

`radio-o-standards` is a useful reference and interoperability target for this
work, but Radio-Oracle should not adopt its ARDF XML schema verbatim without an
explicit compatibility review. Its current XML examples are valuable because
they keep the document root in the IOF 3.0 namespace and put ARDF-specific data
under IOF `Extensions`; that is the compatibility shape Radio-Oracle should
preserve. Avoid designs that make the ARDF namespace the document root for
normal IOF interchange, because those files would no longer be ordinary IOF XML
3.0 documents.

Before implementing the extension surface, resolve the namespace and vocabulary
relationship deliberately: either consume the external `radio-o-standards`
namespace, map it into the OpenARDF `ro` namespace above, or submit standards
changes that converge the two. The initial Radio-Oracle extension should cover
only fields that map cleanly to the app's domain model and current IOF export
surfaces, such as class sex/age metadata, class-required controls,
control-code-to-radio-alias mapping, beacon/spectator/control roles, and
result-side valid-punch counts. Broader transmitter metadata such as schedules,
frequency, modulation, power, polarization, and exclusion-zone geometry should
remain explicit follow-up vocabulary rather than hidden overloads.

Validation for this work must stay local and reproducible. Use Radio-Oracle's
vendored or configured IOF 3.0 schema first, then validate any supported Radio
Orienteering extension schema as an additional layer. Do not make routine
validation depend on remote GitHub schema URLs. When importing, align ambiguous
terms with Radio-Oracle's model before persisting them; for example, be precise
about `Separator` versus spectator controls, `Beacon` versus finish beacons,
and `ValidPunches` versus Radio-Oracle scoring and status rules.

## Completed Milestones

These items were roadmap goals earlier, but are now implemented enough that they
should be treated as current project foundation rather than future work.

### Shared Foundation

- Shared race models and tested Android mapper paths exist.
- Core race validation, result placement, ranking, course evaluation, category
  and control assignment policies, competitor identity fields, and many display
  helpers are shared.
- CSV, TXT, HTML, IOF XML, ARDF JSON-facing policy, ROBIS request metadata, and
  selected import/export paths have shared implementations and tests.
- The desktop smoke target and desktop app test suite are part of the normal
  verification surface.

### Desktop Race-Admin App

- Desktop can create, open, edit, save, and export Race Files.
- Desktop setup workflows cover race settings, categories, controls,
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

### Race Series And Start Fairness

- Race Series manifests group existing Race Files without duplicating core
  race data.
- Desktop Race Series workflows support validation, clean export, competitor
  matching reports, start-fairness summaries, and start-fairness optimization.
- Android can store, list, import, export, and transfer `.roseries` archives.
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
- Shared SQL remains deferred; desktop Race Files are still the right storage
  model for the current desktop app.
- Course Analyzer still lacks map passability knowledge. It does not know
  out-of-bounds areas, dense vegetation, lakes, uncrossable creeks/rivers,
  cliffs, fences, walls, or other barriers unless those effects are approximated
  by imported route geometry or speed factors. Calculated routes still use
  sampled straight control-to-control legs rather than paths selected from a
  terrain-cost or barrier-aware model.

## Medium-Term Roadmap

### Shared Race Services

- Continue extracting platform-neutral result recalculation glue from Android
  `ResultsProcessor` into shared services where it can be tested once and reused
  by Android, desktop, and Race Series tools.
- Keep competitor identity semantics shared. SI number, bib number, call sign,
  Person ID, full-name formatting, and cross-race matching keys must not drift
  between Android, desktop, and series workflows.
- Move durable Android table ordering helpers toward shared comparators when the
  ordering reflects domain policy rather than Android-only UI behavior.
- Gradually remove remaining Android compatibility alias facades in small,
  compile-proven stages.
- Continue auditing Android legacy import/export processors against shared
  `TextResultExports`, `HtmlResultExports`, `IofXmlExports`, and CSV paths so
  desktop and Android semantics do not diverge.

### Race Validation And Error Recovery

- Keep expanding Race Validator coverage for setup consistency, import mistakes,
  category/control mismatches, unused controls, missing SI numbers, duplicate
  labels/codes, suspicious category assignments, and late-workflow edits.
- Use validator logic from Series validation so each member Race File can be
  checked independently before cross-race checks run.
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

- Keep current single-race workflows as the default. Series and championship
  tools should remain opt-in and additive.
- Move desktop-only Race Series reporting and optimization helpers into shared
  code as they stabilize.
- Extend Race Series with scoring and eligibility rules for championship
  standings.
- Add explicit cross-race competitor identity and reconciliation support for
  cases where SI numbers, start numbers, categories, or registration details are
  incomplete or change across days.
- Add competition scoring calculations for overall standings, with configurable
  point/placement rules, category scope, absent-result handling, eligibility, and
  tie-break behavior.
- Add championship exports for overall standings, per-race contributions, and
  start-slot fairness traces as derived outputs over linked Race Files plus
  lightweight series metadata.
- Add a Competition View only after the underlying series metadata,
  reconciliation, scoring, and export behavior is stable.

### Course Analyzer And Route Intelligence

- Add map-informed Course Analyzer modeling by extending the existing protected
  KML/KMZ/GPX course import, category matching, duplicate detection, elevation,
  and analysis pipeline rather than creating a separate analysis subsystem.
  Future timing should combine category factors, race-wide speed factor,
  per-leg `SS=#.##` factors, elevation, vegetation, runnability, barriers,
  water, out-of-bounds constraints, preferred corridors, and other map-derived
  impediments.
- Preserve the current category speed-factor table as a provisional input, not a
  final source of truth.
- Extract a shared course-route optimization core after the current analyzer and
  generator route choices have characterization tests. The first shared layer
  should be pure route-ordering and shortest-effective-path logic; analyzer
  report context, imported-route comparison, wait timing, and fox renumbering
  should remain layered on top.
- Continue improving analyzer import UX so saved, imported, calculated, and
  unsaved analyzer data are always clearly distinguished.

#### Course-File Authoring And KML Boundaries

- Continue supporting ordinary KML/KMZ course construction outside
  Radio-Oracle. Suitable visual authoring tools include QGIS, OCAD,
  OpenOrienteering Mapper, ArcGIS Earth/Pro, Google My Maps, and other editors
  that preserve named point placemarks and named route `LineString` objects.
  QGIS is the preferred free general-purpose desktop option, while OCAD and
  OpenOrienteering Mapper are the natural choices when the course is designed
  against an orienteering map.
- Keep tool limitations visible in operator guidance. ArcGIS Earth is a close
  direct KML/KMZ editor but its desktop application is Windows-only; ArcGIS Pro
  is a licensed professional GIS; Google My Maps is suitable for simple
  browser-based points and lines but is weaker for exact metadata, elevation,
  and sensitive pre-event locations; and GIS round trips through QGIS/GDAL must
  be checked for folder, style, and extension-data changes.
- Keep the Radio-Oracle `Create Course` starter KML as the recommended starting
  point for external editing. External editors must preserve recognizable
  Start, fox, spectator, beacon, and Finish names; use ordinary vector
  `LineString` geometry; and give category routes recognizable names such as
  `M21 route`. Every edited file should be test-imported before it is trusted.
- Treat KML `ExtendedData` as optional enhancement data rather than the primary
  control-matching contract. Some GIS conversions flatten folders, rewrite
  styles, or alter extension data even when point and line geometry survives.
- Document that KML attaches `description` and `ExtendedData` to a feature such
  as the enclosing `Placemark`, not to each coordinate-to-coordinate segment of
  a `LineString`. A route can therefore have one whole-route description but
  not native description text for every segment. Splitting one category route
  into separate described `LineString` placemarks is not a compatible
  Radio-Oracle workaround because those objects can be interpreted as separate
  routes.
- Continue using point/course-object descriptions for per-leg `SS=#.##`: a
  value on Start, a fox, spectator, beacon, or another recognized course object
  applies to the following leg. If arbitrary per-segment notes become a real
  requirement, define structured route metadata keyed to stable course objects
  or route-point identities and add explicit importer/model support rather than
  embedding an undocumented convention in description text.

#### Map-Knowledge Interchange And Preparation

- Use an OGC GeoPackage (`.gpkg`) as the preferred normalized vector-map
  interchange format for map-aware analysis. GeoPackage can hold multiple
  typed layers, attributes, coordinate-system metadata, and spatial indexes in
  one portable file. KML/KMZ remains appropriate for course points and routes,
  but it is not the preferred contract for a semantically classified
  vegetation/barrier dataset.
- Define a versioned Radio-Oracle map-knowledge contract with, at minimum,
  `metadata`, `symbol_rules`, `terrain_areas`, `barriers`, `corridors`, and
  `crossings` layers. Preserve source data in optional `raw_areas`, `raw_lines`,
  and `raw_points` layers so classifications can be audited and regenerated.
- Standardize attributes such as source symbol/code, normalized map class,
  speed or traversal factor, crossability, barrier penalty, confidence, notes,
  source map date, coordinate reference system, and source hash. Keep cost
  factors configurable and reviewable; do not treat a provisional vegetation
  multiplier as a universal competitor-speed truth.
- Support three practical creation paths. The OCAD path exports georeferenced
  point, line, and area objects plus symbol numbers and projection information,
  then combines and classifies them with QGIS/GDAL. The OpenOrienteering Mapper
  path opens `.ocd` or `.omap` data, manually exports a GDAL-supported vector
  format, and then normalizes it with QGIS/GDAL. The direct QGIS path draws or
  classifies the normalized layers in QGIS for small venues or maps whose source
  symbols cannot be mapped reliably.
- Treat QGIS/GDAL as the supported automation surface for inspection,
  reprojection, conversion, validation, spatial indexing, and GeoPackage
  creation. `ogr2ogr`, `ogrinfo`, and QGIS processing models are appropriate
  for repeatable developer/operator workflows, but product code and tests must
  not assume a particular local QGIS application path or installation.
- Do not design an automated conversion pipeline around the stock
  OpenOrienteering Mapper executable. Current Mapper command-line arguments
  open files in the GUI; the application does not provide a supported headless
  `--convert` or `--export` command. A future automated Mapper-based conversion
  path would require either a separately maintained GPL-compatible helper built
  from Mapper's readers/exporters or a purpose-built parser for a documented
  Mapper format such as XML-based `.xmap`.
- Make map import a reviewable operation. The preview should report source and
  target coordinate systems, map extent, overlap with the course, feature
  counts by class, invalid geometries, unknown/custom symbol codes, unclassified
  features, and barriers whose connectivity or crossing interpretation is
  uncertain. Custom OCAD/Mapper symbol sets must never be silently treated as
  standard ISOM symbols solely because their numeric codes look familiar.

#### Staged Map-Aware Analysis

- Slice 1: import and validate the versioned GeoPackage contract, preserve
  provenance, and show the classified layers and unresolved symbol mappings in
  a review surface. Keep the source map separate from the Race File when
  appropriate, but hash it and protect any derived pre-event route/control facts
  through the existing encrypted course-data path.
- Slice 2: analyze existing saved/imported route geometry without changing it.
  Report distance and proportion through each vegetation/runnability class,
  distance along preferred corridors, and intersections with water,
  out-of-bounds areas, cliffs, fences, walls, and other barriers. Barrier
  crossings should be warnings with precise map locations and source-feature
  identities.
- Slice 3: add terrain-adjusted traversal cost along an existing route. Combine
  map-class factors with the existing elevation/climb and category/race/per-leg
  speed model, and show the contribution of each assumption instead of reducing
  the result to an unexplained single number.
- Slice 4: add least-cost path calculation between course objects. Rasterize or
  tessellate the classified map at a documented resolution, represent hard
  barriers as non-traversable, give roads/trails and similar corridors suitable
  costs, preserve intentional fence gaps, and allow bridges, gates, tunnels,
  and designated crossing points to override underlying barriers. Use a
  bounded path algorithm such as A* or Dijkstra and prevent diagonal
  corner-cutting across barriers.
- Slice 5: precompute and cache directed least-cost paths between relevant
  course objects, including geometry, distance, climb, terrain cost, and
  provenance. Costs may be directional because uphill and downhill movement
  differ. Feed these pairwise paths into the existing exhaustive, Sprint-loop,
  and Foxoring heuristic route-order searches instead of rebuilding route-order
  logic inside the map subsystem.
- Slice 6: expose map-aware route geometry, alternate-route explanations,
  barrier warnings, vegetation/corridor breakdowns, confidence, and source-map
  provenance consistently in Course Analyzer UI, PDF, KML, and other analysis
  exports. Keep calculated results advisory when map coverage, classification,
  geometry, or crossing information is incomplete.
- Validate map-aware routing with small synthetic fixtures before using real
  maps: closed and open fence gaps, islands and lakes, bridges over water,
  nested vegetation polygons, overlapping corridors, one-way cliff or slope
  effects where modeled, custom/unknown symbols, CRS transformations, and
  stale-map warnings. Then compare selected real courses against expert route
  choices and field observations rather than tuning solely to match another
  application's output.

### Race Editing Model

- Add autosave plus transaction/undo as a medium-term workflow improvement.
- Ordinary single-step edits should eventually autosave immediately and create a
  one-step undo checkpoint.
- Multi-step tools and bulk actions, including Course Analyzer, course imports,
  test-data insertion, calculated-route saves, and fox renumbering, should run
  inside explicit Race File transactions: stage all intermediate changes, then
  either discard the whole transaction on exit or commit and autosave it as one
  atomic change.
- Undo after a committed transaction should revert the whole transaction, not its
  internal substeps.

### Storage

Shared SQL is not on the critical path. Keep file-backed Race File storage for
desktop while shared domain models, services, and import/export APIs stabilize.
After the desktop file workflow and shared services are stable, run a bounded
shared SQL spike with Room KMP as the baseline candidate. SQLDelight remains the
fallback/comparison option if Room KMP limitations are unacceptable.

Reasons:

- Android already has a mature Room schema, DAOs, relations, migrations, flows,
  and transactions.
- Moving persistence into shared SQL is a storage migration, not a small adapter
  change.
- Desktop Race Files remain useful for transfer, review, testing, and series
  packaging even if shared SQL is added later.

## Acceptance Criteria For Future Work

- Feature work lands behind shared tests when the behavior is platform-neutral.
- Platform-specific behavior lands behind platform smoke tests or documented
  manual hardware validation when automation is not practical.
- Android behavior does not regress when shared code grows.
- Desktop Race File compatibility is preserved across release versions.
- Release candidates pass the relevant `just` wrappers, standards inspection,
  packaging checks, and jDeploy release gates for the surfaces they affect.
