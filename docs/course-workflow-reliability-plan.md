# Course workflow reliability: design through results

Status: accepted implementation plan, 2026-09-06. Implementation is in progress; see [the implementation evidence](course-workflow-implementation-status.md) for delivered work and open gates. This plan does not claim that all slices or acceptance checks are complete.

## Objective and operating contract

Support the normal workflow: Create Course or Move Course and Route Generator → iterative KML/Analyzer work → apply the accepted design to the race courses → save and transfer → collect readouts → export and publish. Restarting design, including moving the venue, must not let an earlier design silently influence current courses or outputs.

The final Apply is the transition at which accepted fox numbering becomes authoritative for the race. Preserve iterative editing and draft KML/PDF export. Avoid a second, competing finalization mechanism beside the existing Apply/Save operations: converge those operations on one shared application service, with clear draft-versus-applied wording.

Required invariants:

1. Each applied race has one coherent course revision. A series records the applied revision of each member; race days may legitimately use different designs.
2. A control's identity, display label/fox slot, SI station assignment, and position are distinct concepts. Identity is not reconstructed from a number embedded in a label or an old ID in newly written data.
3. Applying a design binds the accepted labels, SI assignments, physical locations, category membership, ordered route stops, route geometry, and metrics together. Numbering proposals must never silently choose a different physical SI station assignment.
4. Every assigned control has an unambiguous binding. Sprint slow/fast controls, Beacon, Spectator, Start, Finish, and required waypoints retain their distinct roles. Repeated route visits are preserved even though the map may draw one marker per location.
5. Drafts can be incomplete and can be exported as drafts. Race outputs use the applied revision. Pending edits or incomplete application are visible rather than silently mixed with the applied course.
6. Once a race has recorded activity, a design operation cannot reinterpret its historical punches or silently change scored assignments. The first implementation blocks ordinary design replacement for that race and supports starting a revised race from a copy without recorded activity. A future correction workflow would need explicit scope and separate validation.
7. Save/reopen, encryption changes, and desktop/Android transfer preserve the applied bindings and revision. Protection state and design readiness are separate.
8. Successful course-inclusive export/publication means every requested course artifact was produced and validated. Results-only publishing remains supported when intentionally selected or when a race has no course graphics by design.
9. Reapplying identical content is a semantic no-op. An unrelated category edit cannot change another course's geometry, result mapping, or publication.

## Reuse and cleanup rules

Extend existing components before adding new ones. New code is justified for a missing domain boundary or test seam, not to create another implementation of existing behavior.

| Responsibility | Reuse/converge on | Remove after migration |
| --- | --- | --- |
| File reading, generation, import review | `DesktopCourseFileReader`, existing Create/Move Course operations, Classic/Sprint/Foxoring generators, `DesktopCourseKmlImporter` | Alternative matching or assignment loops created to bypass these operations |
| Bindings and validation | Shared event models, `ControlRoleLabelRules`, `EventValidationRules`, existing import conflict handling | Independent label/ID heuristics in Analyzer, IOF, diagrams, and route-length analysis |
| Apply and propagation | `DesktopCourseAnalysisApplier`, `EventProjectEditor`, existing same-course/category rules | Per-button mutation implementations and partial copies of course payloads |
| Geometry and metrics | Existing route sampler, metrics calculator, projection, elevation providers and retained terrain support | Duplicate leg samplers, metric formulas, and coordinate lookup tables |
| Persistence and transfer | Shared Race File/archive codecs, `DesktopEventSeriesArchiveWorkspace`, existing Android adapters and transactions | Duplicate serializers, private alternate course files, and unnecessary cached authoritative copies |
| Export and publish | Existing KML/PDF/IOF writers, result adapters, shared public-results renderer and publishing engine | Export-specific identity repair and independent course-selection logic |
| Automation | `DesktopAutomationCli`, existing fixtures, test runners, `Justfile`, serial Gradle wrapper | Temporary repro scripts and redundant wrappers once covered by supported commands |

In every slice, inventory callers before editing and list what becomes obsolete. After routing callers through the shared implementation, delete unused private helpers, imports, flags, misleading comments, and tests that only verify the removed implementation. Keep the regression scenarios, public behavior, and supported file compatibility.

Do not mistake compatibility code for dead code. Keep required old-file recovery in one versioned legacy adapter with tests. A repository search is evidence about callers, not proof that serialized fields, reflection/JNI hooks, resources, CLI commands, or public APIs are unused. Check those entry points before deletion. Avoid unrelated cleanup in the mixed working tree.

## Current boundaries to resolve

- `DesktopCourseAnalysisApplier` saves Analyzer labels while intentionally retaining the Setup > Controls mapping. `docs/course-analyzer.md` documents that contract. Changing pre-race Apply requires updating the contract, its callers, and tests together; a diagram-only fix cannot implement finalization.
- Imported assignment updates already replace category controls, and moved controls can invalidate affected stored routes. Reuse these protections rather than adding a separate invalidation system.
- The final full route is currently saved for the selected category; other categories can receive label/order updates without the same full geometry replacement. Propagation must distinguish exactly shared courses from different subsets/routes.
- Desktop diagrams use Analyzer's result-control mode; Android diagrams use shared stored-course rendering; IOF and route-length calculations have separate resolution paths.
- Missing course data or a diagram-generation error can currently yield a publication with omitted graphics. The new output contract must distinguish deliberately absent graphics from a failed requested artifact.
- `sourceSha256` is an imported file digest. It is not the semantic identity of an applied design and must not be repurposed as one.
- Pending local work includes field-label provenance (`resultControlLabelsById`) and route-length identity changes. Review and reconcile that work in the baseline slice; do not introduce an overlapping second provenance mechanism or accidentally include unrelated changes.

## Slice 0 — Establish the baseline and reusable test harness

Deliver a workflow coverage inventory and a deterministic scenario runner before changing identity behavior.

- Record the exact baseline commit and relevant pending changes. Preserve the Fox5 regression and the existing tests for imported numbering, Analyzer numbering, shared courses, stale-location cleanup, encrypted data, and series transfer.
- Extend existing generated-course fixtures to describe expected physical placements, labels, station codes, categories, roles, and routes. Use synthetic coordinates and competitors; real championship archives remain opt-in local inputs.
- Add injectable IDs, time, elevation/declination providers, output directories, and failure points only where existing seams are insufficient. Production defaults continue using the existing services.
- Make the runner invoke the same import, analysis, apply, save, readout, and export operations as the UI. Directly filling final model fields is not an end-to-end workflow test.
- Emit structured reports with each step's outcome, expected/actual bindings, private revision fingerprints, output inventory, and failures. Keep generated evidence under build reports and separate protected local evidence from sanitized CI artifacts.
- Characterize current defects as explicit expected failures in the audit report. Do not hide them with broadly skipped tests or leave the normal test suite permanently red.

Gate: the harness reproduces at least an imported-ID collision, a design renumbering/output disagreement, and an incomplete venue-replacement case. Repeating a fixture yields identical semantic evidence. Existing regressions retain their assertions.

## Slice 1 — One shared binding model, resolver, and revision identity

Define the minimum versioned additions to existing shared models: explicit placement-to-race-control bindings, ordered control references, applied design identity, and dependency fingerprints. Reuse `EventControl` and category assignments; do not create a second control catalog. Keep revision-specific geometry and sensitive provenance within the existing course-protection boundary.

- Define a canonical resolver result: resolved control/position/role with provenance, or missing/ambiguous/conflicting with an actionable explanation. No arbitrary first-match or duplicate-ID collapse on failure.
- Treat imported labels, optional KML identifiers, and description SI hints as import evidence. Resolve conflicts in the import/apply review. Human-readable ideal-order text becomes a presentation/legacy representation of ordered identity references in new data.
- Use explicit applied bindings for new files. Isolate existing legacy heuristics behind one compatibility entry point; exact IDs, recorded mappings, and historical labels can themselves conflict and must be checked together.
- Use semantic fingerprints for invalidation. Separate geometry/route/assignment changes from cosmetic changes; exclude timestamps, machine paths, generated IDs, and randomized encryption bytes from semantic equality. A digest detects change; it does not replace the explicit binding or prove correctness.
- Establish schema handling with the existing Race File codecs and Android storage. Old readers must reject a new required schema or receive an explicitly compatible export; they must not silently discard authoritative bindings and fall back to guesses.
- Migrate unambiguous legacy data in memory, validate, and persist through the normal save transaction. Ambiguous historical data remains unchanged with a specific repair report. No bulk rewrite of user archives.

Gate: bounded exhaustive fox permutations and category subsets preserve identity bijections; UUID IDs, old numeric IDs, changed SI codes, duplicate labels, and Sprint/Foxoring paired identities are covered. Legacy ambiguity is reported without altering stored results. Plaintext/encrypted round trips preserve the same semantic revision.

## Slice 2 — Reliable draft imports, movement, and repeated analysis

Keep edits to the candidate design separate from the applied race revision using the existing session/checkpoint model where possible. Do not add an independently authoritative draft database.

- Preserve explicit object identity in app-generated KML metadata where supported, while retaining normal label/role semantics for external tools. Export/re-import must work if an external editor strips that metadata; missing evidence must trigger matching/review rather than assumed identity.
- Reuse import review to show controls moved, added, removed, renumbered, remapped to SI codes, and categories added/replaced/unmatched. State scope explicitly: selected courses, affected courses, or all courses for the race.
- On movement, invalidate every dependent route, metric, optimized order, and cached result-route estimate. Never retain a stale value as current because its category name or source filename is unchanged.
- Reuse replacement assignment logic; prevent duplicate import from accumulating foxes or inactive mappings. A same-file shortcut must also respect the current design/dependency state.
- Keep partial imports useful for iteration. Unmatched existing categories remain explicitly unchanged or require replacement; they cannot silently be treated as part of the newly completed design.
- Distinguish planning numbering from the applied field mapping in previews and draft exports. Saving/reopening or canceling a draft must have predictable behavior and preserve the applied race.

Gate: import → analyze → renumber → export KML → re-import repeated at least three times remains consistent. A changed file with the same filename is detected. Move Course and individual point movement invalidate all and only affected dependencies. Cancel restores the prior state; duplicate import is a no-op.

## Slice 3 — Apply the accepted design as one coherent operation

Refactor existing calculated-route, numbering-only, generated-course, and import-application paths into a reusable prepare/validate/apply operation. Keep small UI callbacks and CLI adapters around that service.

- Prepare a complete change set showing final fox labels, station assignments, locations, route membership, and affected categories. Pre-race Apply makes accepted numbering authoritative in the control catalog and protected course records together.
- Validate station-code/alias conflicts using existing rules. If the source does not establish a station-to-placement mapping, require that mapping in the review; never infer it merely by assuming Fox N uses SI 130+N or by changing raw punches.
- Update full geometry/order/metrics together for categories sharing the exact course definition. For different subsets or constraints, recompute or explicitly invalidate the affected values. Matching names or control sets alone is not sufficient proof of identical route geometry.
- Include inactive mappings and newly needed categories according to the existing activation rules, with one canonical course definition where routes are identical. Preserve category-specific settings and assignments where they differ.
- Prevent a late asynchronous calculation or Apply based on revision A from overwriting a newer revision B. Revalidation occurs at commit, not only when the preview is built.
- Block ordinary redesign Apply for races with existing recorded activity. Offer a new race copy without recorded activity; leave original readouts, assignments, and publications reproducible. Recalculate Results remains an explicit separate action, not an Apply side effect.
- For a series-wide selection, prepare and validate every selected member before writing any. Members not selected stay unchanged. Report coverage so 'all courses applied' has an exact meaning.

Gate: every applied category's visible labels, catalog mapping, course objects, assigned controls, ordered route, and metrics agree. Repeating Apply produces no semantic change. An ambiguous binding, incomplete required category, stale calculation, or existing recorded activity cannot cause a partial mutation.

## Slice 4 — Persistence, transfer, and legacy recovery

Route all new state through existing shared codecs, course protection, archive workspaces, and Android database adapters.

- For `.roseries`, validate/encode the full candidate, then use the existing checked atomic replacement. Refresh session/materialized views only after successful commit.
- Standalone files use the existing safe save mechanism. Legacy series with multiple external member files need staged writes and an explicit rollback/recovery journal; do not claim a multi-file operation is atomic merely because each individual rename is atomic.
- Verify every selected encrypted member before writes. Wrong password, missing member, cancellation, disk failure, and external file modification must leave the prior valid archive available and session state consistent.
- Transfer the applied revision and bindings through Android Room, exports, archives, and desktop re-import. If mandatory metadata cannot survive an older client, reject that unsupported path clearly.
- Reopening must reconstruct derived data from the saved applied revision, not stale globals, a last-opened KML, or a private materialized directory.
- Keep legacy migration reports reversible and narrow. Retain compatibility adapters until supported old formats have verified replacement coverage.

Gate: desktop → archive → Android → archive → desktop retains assignments, bindings, geometry, and recorded punches. Save/reopen at every workflow checkpoint is equivalent to uninterrupted execution. Failure injection proves no partial series state or silent downgrade.

## Slice 5 — All outputs consume the same resolved course

Introduce a shared, immutable resolved-course projection by extracting/consolidating existing preparation code. Migrate each consumer to it before removing its old resolver.

- Migrate Analyzer's applied-course view, KML/GPX and IOF CourseData, PDF/2D course graphics, desktop/Android public diagrams, and result route-length interpretation.
- Preserve format-specific writers and visual styles where needed. Desktop PNG and shared SVG need not be identical renderers, but their controls, coordinates, labels, roles, and route order must come from the same projection.
- Keep scoring and readout evaluation on the existing shared rules and applied category/control model. Retain raw punches and meaningful repeated/extra visits; missing/unknown punches must not be fabricated as course visits.
- Pin one applied revision per race at export start. An edit during a long export cannot mix old geometry with new labels or result metrics.
- Track expected artifacts and dependencies. A missing requested category diagram or ambiguous coordinate is an export error, not a successful partial course export. Results-only output remains an explicit supported mode.
- Preserve women-youngest-first then men-youngest-first result ordering and omission of empty result categories. Complete archive backups remain lossless. Draft exports remain available and clearly distinguished from applied race outputs.

Gate: independently parsed KML/GPX/XML/JSON and rendered diagram evidence agree on the expected final bindings and routes. XML schema validation passes. Existing historical results keep their meaning. Per-consumer fallback resolvers are removed after their callers use the shared projection.

## Slice 6 — Verified publication and replacement

Extend the existing shared publisher and mirror mechanisms rather than adding another deployment pipeline.

- Build a complete staging directory from a frozen race/series snapshot. Validate artifact coverage, references, result counts, categories, course bindings, and content hashes before replacing the local generated site or starting deployment.
- Use an explicit course-inclusive versus results-only policy. Missing required unlocked course data, a missing selected series member, or failed rendering prevents a claim of complete course publication. Do not log passwords or publish protected provenance.
- Replace obsolete generated files within the targeted publication, including removed diagrams and download links. Preserve unrelated races. Use a generation token/content digest to avoid stale browser artifacts without exposing protected course fingerprints publicly.
- Record deployment identity and outcome. Retry the same snapshot idempotently; distinguish failed generation, failed upload, deployment acceptance, and verification of the public URL.
- Verify fresh public catalog/data/artifact responses after a controlled deployment. A stale response must not be reported as verified success. Preserve the previous known-good deployment on pre-deployment failure; report provider rollback limits honestly.

Gate: publishing revision B over A removes obsolete outputs and exposes only B's expected artifact set. A corrupted/missing artifact blocks completion. Local mock upload tests and a controlled real-download check cover both desktop and Android publication adapters.

## Slice 7 — Full workflow acceptance and release proof

Run the complete matrix on an isolated candidate containing the intended slices. Reuse the existing serial `just`/Gradle build and package recipes. No broad staging of unrelated working-tree changes.

- Add a PR workflow running deterministic shared, desktop, Android/Robolectric, and output-validation gates. Use bounded fixtures and record seeds; expensive terrain/device/deployment runs are separate explicit gates.
- Run final packaged Mac UI smoke checks: create/import, Analyzer iteration, Apply review, save/close/reopen, export, and publish preview. Test both locked and unlocked course access. Use production UI actions/test identifiers, not coordinate-only automation or duplicated business logic.
- Run Android device/emulator archive transfer, persistence, readout processing, and export checks. A fixed synthetic punch stream validates software; a real SPORTident download validates the hardware path separately.
- Run the existing real-archive diagram and retained-terrain acceptance hooks on copies. Verify source archive bytes remain unchanged; keep sensitive artifacts local.
- Perform one controlled publication and fresh-download comparison for the final candidate. Record code commit, app versions, fixture/archive identity, gate outcomes, deployment identity, and remaining device/platform limitations.
- Update `docs/course-analyzer.md`, operator workflow instructions, and tooltips. Document what Apply changes, when courses need review after movement, and how to resume design after recording has begun.

Gate: the complete scenario below passes, all targeted regressions pass, the superseded code inventory is closed, and packaged/remote evidence corresponds to the same candidate. A skipped device or remote gate is reported as unverified, never counted as a pass.

## Automation tools to add through the existing CLI

These are proposed interfaces, not commands available today. Thin `Justfile` recipes will wrap `DesktopAutomationCli`/Gradle operations; extract command handlers into focused files rather than growing its existing dispatcher or `Main.kt` substantially.

| Proposed recipe | Purpose and contract |
| --- | --- |
| `just course-audit <race-or-series>` | Read-only validation of bindings, revisions, category coverage, stale dependencies, and legacy ambiguity; JSON plus readable summary |
| `just course-workflow-test <scenario> [through-step]` | Run real workflow services from a fixture, optionally stopping at a checkpoint; deterministic expected-versus-actual report |
| `just course-apply-preview <race-or-series> <design>` | Build/validate the same change set used by UI Apply; no writes to source inputs |
| `just course-export-verify <race-or-series> <output>` | Generate through production writers, parse artifacts independently, check coverage and geometry; isolated output only |
| `just course-workflow-check` | One aggregate command for the deterministic lifecycle, migration, transfer, and exporter gates |
| `just course-publication-verify <url> <expected-manifest>` | Read-only fresh-download verification of the public artifact inventory and semantic content |

Extend existing `series-validate`, `series-package-fingerprint`, `iof-schema-check`, `classic-route-smoke`, and the published diagram smoke test where their contracts fit. Do not implement a new parser for a format already supported. Independent test output readers are justified only as verification oracles, never as another production importer.

Automation reports include `passed`, `failed`, `blocked`, or `skipped` per gate; dependency failures must not masquerade as success. Invalid CLI input and validation failures return documented nonzero exit codes. Command arguments must not carry passwords or tokens; reuse protected input mechanisms. Test mode must use isolated preferences/log/cache roots and cannot connect to a live station or publish to production implicitly.

## End-to-end scenario and test matrix

Core scenario:

1. Create a synthetic multi-race series using production course creation/generation paths; include Classic 80m/2m, Sprint, and Foxoring.
2. Import draft A. Analyze and accept numbering B. Export KML; re-import and improve it to C. Save/reopen each checkpoint.
3. Apply C to all intended race courses. Assert exact expected final placement/fox/SI/category bindings using a separately authored fixture oracle.
4. Before any readouts, move the venue and remove/add a fox in a new draft. Test a partial replacement that must remain incomplete, cancellation, then a complete Apply D. No A/B/C geometry may appear in D outputs.
5. Transfer to Android and back. Test plaintext, encrypted, wrong-password, mixed-protection series, and activation of previously inactive categories.
6. Feed known Start/Control/Beacon/Finish records through the production readout path. Include correct, alternate, repeated, missing, extra, unknown, and ambiguous punches, plus DNS/DNF cases. Assert expected results and unchanged raw records.
7. Attempt redesign after recording: require the historical race to remain unchanged; create and apply a revision in a new race copy through the supported flow.
8. Export every supported relevant course/result format from both platforms. Compare semantic results, control identities, routes, positions, and coverage. Reopen exported archives and repeat.
9. Publish D to a test destination, then publish an updated artifact set with a removed category. Confirm obsolete links/files are gone and unrelated publications remain. Verify fresh remote downloads against the manifest.

Lower-level matrix, without multiplying every expensive end-to-end run:

- Exhaustive Classic fox-number permutations and supported small control subsets against the resolver; deterministic generated cases for larger Sprint/Foxoring layouts.
- Exact IDs, opaque UUIDs, legacy IDs, stripped KML metadata, renamed labels, changed SI assignments, conflicting hints, role collisions, duplicate import, and identical names on distinct race days.
- Shared courses versus same-control-set/different-route courses; active/inactive mappings; added/removed categories; movement of Start/Finish/Beacon/Spectator as well as foxes.
- Route/elevation invalidation, restored terrain snapshots, missing elevation, magnetic-north projection, and existing metric rounding rules. Use existing calculators; small independent geometric expectations verify them.
- Save/cancel/reopen, interrupted write, external archive change, stale asynchronous analysis, stale session unlock data, missing series member, render/upload failure, and repeated retry.
- Locked/draft/results-only output policy; no protected coordinates or credentials in public results-only artifacts or routine logs.

Use independent expected bindings and output parsers to avoid testing the resolver with its own output as the expected answer. Add semantic render metadata for tests if necessary, but also inspect actual PNG/SVG/PDF output: completeness in a model is insufficient if a label is clipped or obscured. Pixel checks should tolerate platform font differences; semantic counts and positions are the primary cross-platform oracle.

## Delivery order and definition of done

Implement slices 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7. Split a slice into smaller commits when it crosses model, adapter, and UI boundaries, with compilation and focused regression gates at each boundary. New authoritative state is not exposed to users until its persistence and consumers support it. Remove superseded implementations in the same slice that migrates their callers, not in an indefinite later cleanup.

For each slice, record changed responsibilities, reused components, deleted code, tests and evidence, remaining failures, and compatibility implications. Run focused checks first; broader desktop/shared/Android gates belong at integration boundaries and on the final candidate. Avoid repeated full suites without a new change or unresolved concern.

The workflow is done when a user can iterate, apply, move the venue, apply again, transfer, collect results, and export/publish without old design data being mistaken for current race data; every output uses the same applied bindings; historical races remain reproducible; incomplete or ambiguous course publication cannot report success; and the automated acceptance workflow proves those statements from generated inputs through fresh public artifacts.
