# Radio-Oracle Multiplatform Roadmap

Status reviewed: 2026-09-17 against the current source, regression coverage, and
release history through 1.0.48. This review does not replace hardware acceptance.

Radio-Oracle is no longer an Android-only app with a hypothetical desktop beta.
It is a shared Kotlin project with Android race-day workflows, a desktop Race
File workflow, desktop packaging through jDeploy, and growing shared race,
import/export, Course Analyzer, Race Series, and validation services. This
roadmap now tracks the work that remains after the initial multiplatform
foundation and desktop race-admin milestones.

This is a forward-looking document. Completed work belongs in release notes and
project history and is summarized here only when needed to explain the current
state. Conditional ideas without an active requirement should be added only
when they are prioritized.

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

Android and desktop now share the public-results generation and Cloudflare
publishing behavior, including split reports and award exports. SPORTident
station tools on both platforms include clock inspection/synchronization and
read-only field-station Punch History; the Race Series workflow also includes
explicit competitor matching and reconciliation overrides.

Series validation already runs the shared Race Validator on each member Race
File before cross-race checks. The validator covers category/control consistency,
unused controls, missing SI numbers, duplicate labels/codes, and standard-category
assignment checks. Desktop continuous SPORTident readout already uses a platform
serial-device boundary, with stop handling, idle-timeout continuation, and a
post-read removal guard. Developer station diagnostics already measure response
times and compare system-information settings.

Course tools are grouped under `Setup > Courses`. Course imports and calculated
design application use reviewed candidate/commit flows; Analyzer distinguishes
applied courses from drafts and imported geometry from calculated routes.
Pre-import checkpoints support recovery. General autosave and undo remain future
work. See [`course-workflow-implementation-status.md`](course-workflow-implementation-status.md)
for implementation evidence and its separately recorded acceptance limits.

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
about spectator controls, `Beacon` versus finish beacons, and `ValidPunches`
versus Radio-Oracle scoring and status rules.

## Active Boundaries

These are deliberate limits in the current app, not necessarily defects.

- Android remains the primary race-day platform for mature USB readout and
  Bluetooth ESC/POS printing.
- Desktop and Android SPORTident tools now cover readout, time synchronization,
  and read-only field-station Punch History. Live punch streaming, deeper
  diagnostics, multi-station coordination, and additional configuration writes
  still require more hardware validation.
- Local results web server exposure should stay loopback/local unless LAN
  exposure is explicitly hardened and selected.
- OCheckList/new-card import remains future work until sample files or schema
  details are available.
- Shared SQL remains deferred; desktop Race Files are still the right storage
  model for the current desktop app.
- Single-race workflows remain the default; Series and championship tools are
  opt-in and additive.
- Course Analyzer still lacks map passability knowledge. It does not know
  out-of-bounds areas, dense vegetation, lakes, uncrossable creeks/rivers,
  cliffs, fences, walls, or other barriers unless those effects are approximated
  by imported route geometry or speed factors. Calculated routes still use
  sampled straight control-to-control legs rather than paths selected from a
  terrain-cost or barrier-aware model.

## Near-Term Roadmap

### Cloudflare Settings Transfer

- Add an explicit desktop-to-Android transfer for the Cloudflare Pages project,
  branch, account ID, API token, and retained-results publishing mode. Keep these
  app-level credentials out of Race Files and Race Series archives.
- Reuse the established local desktop-to-Android transfer experience, but use a
  versioned settings payload with authenticated encryption rather than sending
  the API token through the current plaintext Race File download endpoint.
- Make every transfer short-lived and single-use. Require confirmation on the
  Android device before replacing existing settings, keep the API token masked,
  and never include credential values in logs, status text, or diagnostics.
- Put payload validation and normalization in shared code so desktop and Android
  accept the same fields and defaults. Cover expiry, replay, tampering,
  incomplete settings, cancellation, and explicit overwrite behavior in tests.

### Elevation Profiles In Results Exports

- Add course elevation profile diagrams to all results exports, with particular
  priority on the Cloudflare public-results export.
- Reuse course route geometry and elevation data consistently across Android
  and desktop exports. Show distance and elevation units clearly, handle missing
  elevation data explicitly, and honor existing protected course-data rules.

### Import And Export Format Guidance

- Extend the CSV and KML/KMZ format guidance pattern to other supported import
  and export formats, including GPX and XML. Place basic information beside each
  corresponding file action on desktop and Android, with expandable details.
- Explain each format's purpose, accepted file types and versions, expected
  structure, required names/identifiers, coordinate and elevation conventions,
  and which data the action imports or exports. Distinguish XML dialects and
  document types, such as IOF CourseData, EntryList, StartList, and ResultList,
  instead of presenting XML as a single interchangeable format.
- Include small examples and explain supported features, omitted data, and
  round-trip limitations. Link to existing starter files, templates, or
  documentation where available.
- Reuse the existing guidance UI and shared metadata wherever practical. Verify
  guidance against the actual importers and exporters, with representative
  fixtures, while preserving file formats, compatibility, import review, and
  protected course-data behavior.

### Manual Control And Course Editing

Manual category control assignments currently define a scoring course, but do
not assemble geographic course data or automatically calculate an ideal route
for a new category. Add coordinate-based entry and editing workflows within
`Setup > Courses` that reach the same accepted course state as an analyzed import;
this work need not wait for the full visual map editor.

- Extend the existing protected `Update Location` workflow to support initial
  coordinate entry within `Controls` for starts, foxes, beacons, finishes, and
  other supported course points. Reuse canonical control identities and
  protected location storage, preserving Race Password protection.
- Add `New Course` with a course-name field and selection of existing controls,
  start, beacon, finish, and any required intermediate points. Permit courses
  to select different starts and finishes. Support building courses after an
  import that supplied only controls, without requiring a KML/XML round trip.
- Extend the existing controls editor for manual control creation and editing,
  including public labels, SI codes, supported point roles, and coordinates.
  Add editing of existing course names, point membership/order where applicable,
  start/finish selection, and category assignments. Preserve stable identities,
  validate duplicate labels/codes and affected references, and review changes
  before acceptance using the existing candidate/commit workflow.
- Provide `Analyze Course`, reusing the import analysis and elevation pipeline
  to calculate ideal order, total horizontal length, total climb, and effective
  length. Use effective length when sufficient elevation data exists; otherwise
  optimize horizontal length and clearly show unavailable climb/effective
  length rather than inventing values.
- Present the course report before `Add Course` accepts the result. Keep the
  candidate temporary until acceptance, and discard it on rejection. Offer
  assignment to existing categories or creation of a same-named category;
  courses left unassigned must remain visible and manageable in Courses.
- Make accepted manual courses available to Course Report, Course Analyzer,
  category assignment, scoring, persistence, and export through the same course
  model as accepted imports. Do not introduce a separate manual-course store
  or route optimizer.
- When shared coordinates or course membership change, identify every affected
  course, invalidate its prior calculations, and provide recalculation. Preserve
  existing restrictions on replacing designs with recorded readouts, and make
  blocked actions explain how to proceed.
- Validate coordinates, required point roles, and location availability before
  analysis. Add parity tests demonstrating that equivalent manual and imported
  inputs produce equivalent routes, metrics, reports, and saved course data,
  including missing elevation, shared-control edits, protected races, and
  cancellation without changing the accepted race.

### Runner Accountability

- Extend the existing In forest and over-limit views with confirmed start,
  return, and retirement/check-in records. Distinguish a scheduled start from a
  confirmed departure, and a result/readout from confirmation that a competitor
  is safely back. Permit return confirmation without a card download and keep
  whereabouts separate from scoring status.
- Show competitors whose return is unconfirmed, elapsed time and overdue state,
  and the evidence/time of the latest confirmation. Support an offline printable
  accountability list and an explicit end-of-race check that everyone is accounted
  for; neither missing readouts nor scheduled starts alone establish whereabouts.
- Preserve an audit trail for manual confirmations/corrections and validate DNS,
  retirement without readout, late downloads, unscheduled starts, and practice
  repeat runs. Start with local race-day operation; coordinated updates from
  multiple operators depend on the later collaboration milestone.

### Crash Recovery And Verified Backups

- Strengthen race-day persistence independently of the long-term general
  autosave/undo work. Make accepted readouts and explicit saves crash-safe with
  atomic replacement and clear success/failure reporting; preserve the last
  known-good Race File or Series archive if a write is interrupted.
- Reuse existing import checkpoints and backup/archive helpers for rotating,
  versioned backups. State which accepted data is durable and when the latest
  recoverable snapshot was taken; preserve protected course data in backups.
- Provide a reviewed restore workflow that validates file/schema integrity,
  identifies the saved event and snapshot time, and preserves the current data
  before replacement. Test interrupted writes, disk-full errors, corrupted
  files, and restoration of readouts, course data, and Series links.
- Establish the local backup/restore foundation near-term; validate wider
  race-day crash and power-loss recovery as a follow-on mid-term acceptance gate.
  Do not describe backups as verified until representative restore checks pass.

## Medium-Term Roadmap

### Race Format And Level Cleanup

- Audit the user-facing event choices and retain only the essential, justified
  set. Review Other, Practice, District, Regional, National, International, and
  legacy format choices for a clear purpose; consolidate redundant choices and
  document the behavior of every retained option.
- Separate course/rule format (such as Classic, Sprint, Foxoring, or traditional
  Orienteering), practice-versus-competition behavior, and competition/award
  scope where these represent different decisions. Choose the smallest useful
  model and consistent desktop/Android labels rather than letting an ambiguous
  level implicitly select unrelated policies.
- Define and test a behavior matrix for setup defaults, required controls,
  validation severity, start/timing handling, repeated card downloads, result
  matching and scoring, course-edit restrictions, awards/eligibility, Series
  participation, printing, and publishing. Preserve useful practice workflows
  and make every retained choice behave logically across those surfaces.
- Use shared policies for these decisions. Explain unsupported combinations
  before accepting them; changing an event choice must preview affected data
  and calculations rather than silently reinterpret existing results.
- Preserve historical Race Files, Android data, Series archives, and interchange
  values through explicit compatibility mappings/migrations. Never reuse stored
  enum values for a different meaning or silently convert unknown legacy choices
  into Practice. Cover old-file import, save/reopen, and export round trips.
- Enable the simplified choices only after the retained combinations have
  platform acceptance coverage; retired choices may remain readable without
  remaining selectable for new events.

### Traditional Orienteering Support

Traditional orienteering has an existing race type, ordered-control evaluation,
and some regression coverage, but it is not yet a validated end-to-end workflow.
Desktop currently displays existing Orienteering Race Files but does not offer
the format when creating a new event. Complete and validate this support as a
mid-term goal on Android and desktop.

- Start with individual foot-orienteering events on ordered courses. Audit race
  creation, controls, courses, category assignments, entries, start lists,
  SPORTident downloads, finish tickets, splits, places, and public results.
  Enable new-event selection only when this complete workflow is validated.
- Preserve the required course order, including repeated visits to the same
  control and shared controls across different courses. Validate missing,
  out-of-order, duplicate, and extra punches without applying radio-specific
  unordered-fox or Sprint-loop rules.
- Verify sport-appropriate timing, completion/status, ranking, tie handling,
  and assigned-versus-punched start behavior against the applicable event rules.
  Keep existing radio-orienteering scoring unchanged and reuse shared services
  with explicit format policies rather than maintaining a separate evaluator.
- Use conventional control codes and course terminology throughout setup,
  readout, reports, printing, and exports. Remove inappropriate fox, beacon,
  transmitter, band, and schedule requirements from traditional-orienteering
  workflows while preserving ordinary start and finish handling.
- Validate IOF XML 3.0 CourseData, EntryList, StartList, and ResultList interchange
  with representative traditional-orienteering fixtures and external tools.
  Include Livelox export in the compatibility checks as that integration becomes
  available. Preserve course/category relationships and state unsupported data
  clearly during import review.
- Add shared characterization tests and platform acceptance scenarios covering
  setup through save/reopen, real-card readout, corrections, printing, and export.
  Exercise representative club-event data and record field acceptance before
  describing traditional orienteering as supported for race-day use.
- Treat score-orienteering, relays, forked courses, and mass/chasing starts as
  separately scoped extensions after the individual ordered-course foundation
  is stable; do not imply that the initial milestone supports every discipline.

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

- Extend the existing Race Validator with additional import-mistake,
  suspicious-assignment, and late-workflow-edit cases as concrete gaps are
  identified.
- Prefer source-of-truth repairs over display-only fixes: Setup > Controls owns
  SI-code-to-public-label mapping, Setup > Categories owns assigned controls,
  and downloaded SI readouts remain definitive evidence of visited station
  codes.

### Race Incidents And Adjudication

- Add an organizer-reviewed record of transmitter/station failures, missing or
  misplaced controls, timing incidents, protests, and supporting evidence.
  Identify affected categories, courses, competitors, and time intervals without
  overwriting original readouts or treating every incident as a missing punch.
- Record organizer/jury decisions, their applicable rules, rationale, and time.
  Preview affected results before applying an authorized correction, withdrawal,
  disqualification, or course/category voiding. Available remedies must follow
  the selected event rules; do not automatically exclude legs or adjust times
  simply because a failure occurred.
- Build on existing manual result/status editing and official-publication checks.
  Recalculate accepted changes consistently across places, splits, awards, Series,
  and exports; identify previously sent/published outputs needing replacement.
  Preserve an auditable before/after trail and require review when reopening
  official results.
- Export incident and decision records for event reporting. Validate ambiguous
  evidence, multiple affected competitors, later decision revisions, cancellation,
  and the boundary between punch recovery and wider fairness decisions.

### Create A Course From A Downloaded Result

- Add a reviewed workflow for deriving a course from a selected competitor's
  downloaded SPORTident result/readout. Use the recorded station codes and
  punch sequence as evidence of visited controls, resolving them through the
  Race File's canonical controls and public labels.
- Preview the proposed course and flag unknown station codes, repeated punches,
  missing start/finish information, and controls whose roles or intended course
  membership are uncertain. Let the organizer select the included points and
  confirm start, finish, beacon/spectator handling, and course name. An observed
  visit sequence must not silently become a mandatory order for an unordered
  radio-orienteering course or be treated as proof of the intended course.
- Reuse saved control coordinates when available. A card readout supplies punch
  codes and times, not control locations or the competitor's geographic track;
  report missing locations explicitly and request coordinate entry before
  geographic analysis. Do not infer a traveled route, distance, or climb from
  punch times alone.
- Keep the derived course temporary until accepted, then use the same course
  model, analysis, category-assignment, persistence, protection, and export
  workflows as manual and imported courses. Preserve the source readout and its
  result; creating a course must not silently change scoring or replace an
  existing design. Apply existing restrictions for races with recorded readouts
  and review any subsequent category reassignment or recalculation separately.
- Cover code-to-control matching, duplicate/unknown punches, missing coordinates,
  ordered and unordered courses, protected races, and cancellation in shared
  characterization and parity tests.
- Keep this distinct from station Punch History recovery: course derivation
  creates a course from an existing result, while recovery reconstructs missing
  competitor punches or readouts from station backup records.

### SPORTident And Hardware

- Build a shared, specification-backed SPORTident characterization suite before
  broadening protocol behavior. Cover SI5, SI6, SI6*, SI8, SI9, pCard, tCard,
  SI-Card10/11, and SIAC memory layouts; card-number family boundaries; maximum
  punch counts; card-holder fields; CRC vectors; block ordering; erased and
  zero-filled bytes; and malformed or incomplete reads. Use vendor documentation
  and a licensed reference implementation as private behavioral oracles, but do
  not commit proprietary binaries, restricted documentation, or license keys to
  the public repository.
- Audit protocol and model boundaries exposed by those fixtures. In particular,
  verify permanent SI6* numbers above 9,999,999, the intended application limit
  for encoded control codes above 511, tCard's 25 eight-byte records, SIAC owner
  data that extends beyond block 0, and subsecond start/finish/readout semantics.
  Preserve whole-second behavior where event rules require it, but do not
  silently discard available precision before the scoring/export boundary.
- Harden station discovery and remote/coupled-station communication against the
  documented startup and retry cases. Characterize the double-`STX` wakeup
  sequence, legacy base-protocol station detection, and bounded NAK retry/backoff
  on real expendable hardware before changing the currently proven readout path.
- Complete comparative hardware validation using the existing response-timing
  and settings-comparison diagnostics on known-good and suspect download
  stations. Preserve the readout service's existing behavior of allowing flagged
  download-capable stations and blocking clearly non-download modes.
- Extend the existing read-only Punch History download/viewer into a reviewed
  race-recovery workflow. For example, when a competitor's card download lacks
  a punch, inspect the retrieved station's history for the competitor's SI card
  number, station code, and a plausible timestamp within the event. Flag clock
  discrepancies, ambiguous matches, gaps, and duplicates; preview recovered
  punches or readouts and require explicit organizer selection before applying
  anything to a Race File.
- After an accepted recovery, recalculate the affected status, score, splits,
  and placing, refresh derived exports, and mark affected published/sent results
  as needing an update. Preserve the original card download and an auditable
  before/after trail with source station identity, backup-record reference, and
  recovery decision. Cover missing, conflicting, and duplicate records,
  timestamp ambiguity, and cancellation without changing results in tests.
  If backup erase/reset is ever added, keep it as a separate destructive
  maintenance action with explicit confirmation and immediate read-back
  verification.
- Treat live trigger/punch record ingestion as a follow-on to backup recovery.
  Preserve station and card identity, subsecond time, and backup-memory record
  addresses so missed auto-send records can be detected, recovered, deduplicated,
  and audited rather than accepted as an unverified best-effort stream.
- Keep configuration writes beyond the existing clock synchronization behind a
  guarded maintenance phase. A "set attached download box to READOUT" action may
  be added only after the configuration transaction is verified against real
  hardware and has immediate read-back validation.
- Extend the desktop SPORTident SI Card owner-information inspector into a
  programming tool for writing the owner's name to supported cards. Keep card
  family rules, owner parsing, and name preparation in shared code for later Android reuse;
  add Android UI and transport integration separately. Identify supported
  card/station combinations and name encoding/length limits,
  verify the actual write protocol and capacity, then verify the written name
  by reading it back. The desktop SI-Card8 editor now previews owner text using
  a provisional 24-byte ASCII limit; it does not write cards.
  Validate the write transaction on real hardware and preserve card identity
  and unrelated card data. This workflow primarily targets new cards;
  if programming clears existing punches, disclose that consequence before writing.
- Add explicit multi-download-station support so desktop can detect multiple
  connected stations, show their serial numbers/modes/ports, let the user choose
  or assign active stations, and prevent independent readout loops from fighting
  over the same serial device.
- Add a batch readout time-correction tool for common station clock mistakes.
  It should preview affected punches, apply signed offsets only to explicitly
  selected control punches, preserve an auditable before/after trail, and then
  recompute status, score, splits, places, exports, and sent/unsent state.

### Livelox Event Export And Integration

- Add an organizer-facing Livelox export workflow so an event prepared in
  Radio-Oracle can be used for Livelox tracking and post-race route review.
  Start with event setup export; participant tracking remains in Livelox's
  existing workflows. Follow the public
  [Livelox API documentation](https://www.livelox.com/Documentation/Api) and
  [event integration workflow](https://www.livelox.com/Documentation/Api/EventIntegration).
- Use user-delegated OAuth2 Authorization Code with PKCE and the event-import
  scope. Keep access and refresh tokens in platform-appropriate secure storage,
  outside Race Files, Series archives, logs, and diagnostics. Provide clear
  sign-in, cancellation, expired-authorization, and disconnect behavior.
- Export event name, date/time interval, time zone, organizer/location details,
  categories, course assignments, and supported course/control data. Reuse
  shared models and the existing IOF XML 3.0 CourseData exporter. Preserve
  canonical control identities and make radio-orienteering mapping limitations
  visible, including unordered fox visits, beacons, spectator controls, and
  radio-specific extensions that Livelox may not interpret.
- Require a usable georeferenced map for the initial workflow. Start with a
  raster image plus world file or coordinate mapping; consider supported KMZ
  maps after representative-file validation. Review coordinate systems, map
  coverage, control alignment, and category/course connections before upload.
  Reuse Course Designer map calibration as it becomes available, while allowing
  an externally prepared map without requiring the full visual designer.
- Provide a reviewed export candidate, upload its event package and referenced
  files, display Livelox validation errors and warnings, then open Livelox for
  the organizer to complete the import into a new or existing event. Distinguish
  uploaded data from a completed import, retain non-secret event references for
  subsequent updates, and handle cancellation, partial uploads, bounded retries,
  and duplicate-prevention explicitly. Preserve Race Password protection and
  require an explicit export decision before sharing protected course data.
- Add start-list and result-list updates as a later stage after verifying the
  available API operations and reliable asynchronous completion/status feedback.
  Desktop and Android clients must not depend on receiving inbound webhooks.
  Show pending, successful, and failed updates accurately, and expose event/class
  viewer links when available.
- Keep any later GPX/TCX/FIT participant route upload separate from organizer
  export, with the participant's authorization and the route-import scope.
  Do not use user-delegated route import to bulk-upload other participants'
  tracks. Follow the public
  [route integration documentation](https://www.livelox.com/Documentation/Api/RouteIntegration).
- Share payload construction, mapping, validation, and provider interfaces
  across desktop and Android, with platform-specific authorization and file
  access. Reuse standards-based exports for other compatible applications;
  assess each provider's API separately rather than assuming universal support.
  Validate with representative maps and courses, mocked authorization/upload
  failures, and an organizer-approved end-to-end Livelox import before release.

### Live Results Providers

- Add non-ROBIS live-result providers after their network/result-service logic is
  isolated from Android WorkManager and represented through shared provider
  interfaces.

### Competition And Series

- Move desktop-only Race Series reporting and optimization helpers into shared
  code as they stabilize.
- Extend Race Series with championship overall scoring, including configurable
  point/placement rules, category scope, absent-result handling, eligibility,
  and tie-break behavior.
- Add championship exports for overall standings, per-race contributions, and
  start-slot fairness traces as derived outputs over linked Race Files plus
  lightweight series metadata.
- Add a Competition View only after the underlying series metadata,
  reconciliation, scoring, and export behavior is stable.

## Long-Term Roadmap

### Multiple Race-Day Operators

- Support coordinated registration, start, return confirmation, and download
  operators across desktop and Android while retaining useful offline operation.
  Define authoritative event ownership, operator responsibilities, and permitted
  edits before adding synchronization.
- Preserve stable competitor/control/readout identities and provenance. Detect
  conflicting assignments or edits, deduplicate repeated downloads, and review
  reconciliation after disconnected work; do not allow competing saves or
  last-write-wins behavior to silently discard accepted data.
- Provide clear connection, pending-update, and conflict status with durable
  local queues and bounded retries. Separate operator access from public result
  viewing and protect credentials and pre-event course data.
- Reuse shared services and existing transfer boundaries. Validate simultaneous
  edits, disconnect/reconnect, operator/device failure, clock differences, and
  recovery from divergent snapshots before race-day deployment. Multiple SI
  stations alone do not constitute a complete multi-operator workflow.

### Course Designer

- Add a first-class visual Course Designer for placing controls, creating
  category routes and simple graphics, editing KML-compatible appearance, and
  applying the result to a Race File or exporting only the authored overlays.
- Build the visual editor into the existing `Setup > Courses` workspace,
  retaining its controls editor, imports/exports, elevation tools, review,
  protection, and destructive actions.
- Start with offline JPG/PNG maps using world files, explicit coordinates, or
  manual calibration. Add GeoTIFF and broad CRS handling after the coordinate
  model is stable, and treat robust GeoPDF support as a later, separately
  validated milestone.
- Keep a versioned editable Radio-Oracle course-design document as the source of
  truth. KML/KMZ remains an interchange output and should omit the base map by
  default.
- Reuse existing Radio-Oracle KML/KMZ parsing, styles, exports, 2D rendering,
  import review, protected-course rules, category matching, and Course Analyzer
  behavior rather than creating a parallel course subsystem.
- Use Purple Pen as the principal course-editor UX and algorithm reference. Use
  QGIS and OpenOrienteering Mapper as geospatial workflow references and
  evaluate permissively licensed supporting components, especially GDAL, PROJ,
  PDFBox, and PDFium, without embedding GPL application code into an
  otherwise MIT-only distribution by accident.
- Follow the complete workflow, architecture, component/licensing assessment,
  staged implementation, compatibility rules, risks, and acceptance criteria in
  [`course-designer-plan.md`](course-designer-plan.md).

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

#### Course-File Authoring And KML Boundaries

- Until the native Course Designer reaches parity, continue supporting ordinary
  KML/KMZ course construction outside Radio-Oracle. Suitable visual authoring
  tools include QGIS, OCAD, OpenOrienteering Mapper, ArcGIS Earth/Pro, Google My
  Maps, and other editors that preserve named point placemarks and named route
  `LineString` objects. QGIS is the preferred free general-purpose desktop
  option, while OCAD and OpenOrienteering Mapper are natural choices when the
  course is designed against an orienteering map.
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

- Add general autosave and undo on top of the existing course draft/apply,
  reviewed-import transactions, and pre-import recovery checkpoints.
- Make ordinary single-step edits autosave immediately and create a one-step
  undo checkpoint.
- Extend transaction coverage to remaining multi-step tools and bulk actions,
  including test-data insertion. Reuse the course workflow's candidate/commit
  services and add atomic autosave when a transaction is accepted; discard
  unaccepted changes on cancellation.
- Add undo that reverts an entire committed transaction, including course
  imports, calculated-route application, and fox renumbering, as one change.

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
