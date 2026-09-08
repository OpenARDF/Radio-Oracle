# Radio-O Manager 0.3.0 adoption plan

Planning baseline: September 7, 2026. Radio-Oracle `Development1` was clean at `74a197e5`; upstream 0.3.0 is `01a73922e7bee47f1a7dfbabf6e272834c50577d`. Recheck the checkout before implementation. This document authorizes no implementation, installation, publication, or production-data changes by itself.

Bring in the improvements as small, independently reviewable changes. Use upstream commits as references, preserve attribution, and adapt behavior to Radio-Oracle's shared models and existing helpers. Avoid a wholesale upstream merge. Keep data migrations separate from the first bug-fix batch so those fixes can be validated and released independently.

## 0. Establish the comparison and validation baseline

- Record the current branch, commit, working-tree changes, upstream tag, and affected import/export paths. Preserve unrelated work and prepare one focused commit per concern during implementation.
- Capture synthetic or sanitized fixtures covering existing Radio-Oracle files and 0.3.0 exports. Include classic, sprint, foxoring, short, and orienteering races; matched/unmatched results; drawn/punched/edited starts; and beacon/spectator controls. Record each fixture's origin and expected meaning. Retain MIT attribution for borrowed code and fixtures.
- Run the existing relevant tests once before edits so existing failures are distinguishable from regressions. Use `just --list` and the existing Temurin 17/sequential Gradle wrappers. Do not launch competing Gradle gates.
- Before changing interchange behavior, compare the current `radio-o-standards` schema, examples, and README under `docs/standards-compatibility-policy.md`. Record compatibility per affected format. Upstream application's behavior alone is not proof of standards compliance. A new standards capability or intentional divergence requires the policy's linked standards proposal before merge; prepare the concrete proposal before any external submission.
- Use copies for import, migration, installation, and device acceptance. Preserve a restorable native Race File/series export and database backup where the test requires existing database state.

Exit: the targeted baseline is understood, expected fixture behavior is documented, and implementation scopes are tied to actual paths. No full-release or dependency-upgrade work is needed for this baseline.

## 1. Fix crashes and Android report output

Three focused changes:

1. Add separate emptiness checks before removing first START and last FINISH punches. Use the completed 0.3.0 behavior, not just upstream's initial incomplete guard (`b573d2e8`, `f5e3e5d9`). Preserve existing scoring and start-time precedence.
2. Correct the Android HTML/TXT category length label to meters (`46cfe94e`). Keep stored values unchanged and check the actual generated document: 4,500 stored meters must not display as 4,500 km. Do not change desktop's already converted kilometer output just to match a template edit.
3. Replace hardcoded Android HTML result headings with localized resources. Preserve Radio-Oracle's “Person ID” label, canonical result-category ordering, and empty-category omission on result surfaces. Backup/setup surfaces retain their full data.

Validation: extend the existing result-evaluation tests for empty, START-only, FINISH-only, START-plus-FINISH, and ordinary readouts. Verify ordinary scoring, punched/edited start preservation, and manual result status. Render representative HTML/TXT exports in English and Czech and inspect labels, values, headings, and category ordering. Reuse existing export tests where they cover meaningful behavior; avoid a new test harness for literal-only template edits.

Exit: these fixes pass Android checks, require no database/schema change, and can stand alone as a bug-fix candidate.

## 2. Make 0.3.0 imports compatible across Android and shared code

Implement two changes independently, backed by common expected fixture results.

**Drawn-start fallback** (`95024d42`, `796fafe8`): retain an explicit result start; otherwise derive an effective start from the competitor's drawn offset and race start. Preserve an explicit finish independently of whether an explicit start exists. Reconstruct splits from the effective start using existing timing helpers. If neither source supplies a start, retain the incomplete-data state rather than inventing one. Keep Android and shared import semantics equivalent. This phase does not introduce persistent start provenance or make schedule edits automatically change existing results.

**BEACON/SEPARATOR punches** (`25a2f16c`): accept these known interchange roles through a shared mapping into Radio-Oracle's course/punch model. Preserve SI code, control role, sequence, status, and split time. Do not assume adding SI enum members is sufficient, copy upstream scoring changes, or convert a beacon's code to zero. Specify how role information survives when category/course metadata is absent or conflicts; do not silently discard it. Use “Spectator” in visible text and the required SEPARATOR token in interchange. Keep unrelated unknown values under an explicit validation policy.

Validation: extend Android `RaceJsonTests`/`ResultJsonTests` and shared `RaceBackupJsonImportsTest`, using equivalent cases. Cover explicit versus drawn starts, missing finish, neither start source, start offset zero, midnight/week boundaries, SI5 timing assumptions, unmatched readouts, beacon/spectator SI codes, aliases, missing/conflicting course definitions, and malformed input. Verify failed imports do not leave partially committed races. Compare imported semantic values and native save/reopen/export results across platforms; byte-identical output is not required where identifiers are regenerated. Existing `.rom.json` and series behavior must remain intact.

Exit: representative 0.3.0 files import correctly on both paths; existing fixtures still pass; no silent role/code/time loss remains; standards compatibility is recorded. These fixes can join phase 1 without waiting for later features.

## 3. Add contained Android usability improvements

Deliver each item separately so any troublesome change can be reverted without losing the others.

| Change | Implementation boundary | Acceptance |
|---|---|---|
| Result-service error indicator (`0e6140a0`) | Reflect RUNNING, disabled, network, authorization, and error states; include readable/accessibility status text. Preserve Cloudflare's separate status handling. | Exercise success, failure, recovery, and disabled transitions; enabled-but-failing must not look healthy. |
| Newest races first (`6a8d41b4`) | Reverse main race-list date ordering; preserve stable selection and existing series grouping. | Inspect several dates, equal dates, and reopening the selected race. |
| Standard beacon alias (`c82419ee`) | Add SI 99 with B/M through the existing standard-alias mechanism. | Check English/Czech generation, duplicate handling, sorting, and preservation of customized aliases. |
| Competitor view retention (`95fa276f`) | Retain the selected table display mode through view recreation. Define its reset behavior when changing races and guard stale/out-of-range values. | Navigate to an edit dialog and back, rotate the device, and change races. Do not claim full process-death persistence unless implemented and tested. |
| Printing (`50c2db48`, relevant parts of `3d6a78d0`) | Add leading-feed preference defaulting to zero; budget summary-row width after choosing displayed place/status/time. Reuse current finish-ticket formatters and printer lifecycle. | Check long names, non-OK statuses, narrow paper, zero/nonzero feed, single/double printing, and disconnect recovery. Inspect real paper output before marking printer support verified. |

Use focused existing tests for state/data behavior and device inspection for presentation. These changes need no timing-provenance migration.

## 4. Add bulk competitor moves as a separate feature

Adapt the category-menu action from `df0a5ba9` through Radio-Oracle's current persistence and result-update helpers.

- Preview source category, destination, and affected competitor count. Require an explicit destination and offer cancel before applying the move.
- Scope both categories and all competitors to the current race. Handle “No category” explicitly; never use SQL equality to NULL to find unassigned competitors.
- Preserve competitor identity, SI/rental details, start assignments, and raw punches. Re-evaluate category-dependent results and mark affected remote results as needing update. Keep any asynchronous recalculation state visible and recoverable.
- Make the database mutation atomic, using existing transaction helpers if available. Define recovery for recalculation failure instead of allowing a partly moved field.

Validation: empty source, same source/destination, assigned/unassigned destination, invalid cross-race destination, competitors with readouts, manually assigned statuses, and save/reopen. Confirm the resulting evaluations agree with equivalent individual category edits. Verify cancellation and injected failure leave the expected state.

Exit: one confirmed operation moves the intended field without losing identity or leaving misleading results. This feature is independent of start provenance.

## 5. Design and implement start provenance only after compatibility fixes

The existing guard already protects recorded starts from the headline overwrite bug. The additional feature is controlled updating of results that genuinely derived their start from a draw. This warrants its own design, migration, and acceptance cycle.

- Define a shared source model that distinguishes known punched, known drawn, manually selected/edited, and legacy unknown sources, or an equivalent representation. Distinguish edits to start time from unrelated readout edits.
- Preserve existing times for legacy unknown results. Equality with a drawn time is insufficient proof that the result was drawn. Do not classify all old rows using upstream's PUNCHED default and then claim provenance is known.
- Specify precedence for card readout, manual edits, imports, competitor/category reassignment, race-zero changes, cleared starts, and regenerated start lists. Only confidently drawn, unoverridden results may follow a schedule change; show the affected results before a bulk change.
- Persist the same meaning through Room, shared models, native Race Files, and series transfers. Verify older-file reading and older-app behavior explicitly. Keep private provenance out of standards-facing formats unless the contract permits it.
- Add a new migration appropriate to the implementation-time database version. Do not copy upstream's version 4-to-5 migration into Radio-Oracle's version 13 history.

Validation: a precedence matrix for punched/drawn/manual/unknown sources; upgrades from supported older databases; old/new native files; Android-to-desktop-to-Android transfer; repeated save/reopen; and recalculation after schedule edits. Confirm legacy results remain numerically unchanged unless the operator explicitly changes them.

Recovery: test migration against disposable database copies. Preserve the original database and compatible application version. Recovery may require restoring that backup; reverting code does not safely downgrade a migrated database.

Exit: migration and cross-platform tests pass, provenance rules are documented, and no inferred legacy source can silently rewrite historical timing.

## 6. Expose shared start-list drawing on Android

Use `EventProjectEditor.drawStartList`, persisted `StartDrawSettings`, existing start-number assignment, and start-list quality checks. Borrow upstream's interaction ideas rather than its independent drawing engine.

- First deliver settings, generation, preview, quality warnings, explicit Apply, and Cancel. Preserve native-file transfer and the shared rules for seed, interval, clubs, starters per time, and series constraints.
- Define treatment of competitors with recorded results or locked starts using phase 5's rules before allowing redraws to affect them.
- Add drag-and-drop category scheduling only after specifying how its constraints fit the shared model and desktop behavior. Defer this interaction if it would introduce Android-only rules or unrepresentable settings.

Validation: equivalent inputs/settings/seed give equivalent assignments on Android and desktop; all supported race formats work; cancellation changes nothing; applying/reopening preserves settings, quality indicators, starts, and start numbers. Test a realistic field size, phone/tablet layouts, rotation, and series transfer.

Exit: Android uses the established engine, and a saved draw can be explained and reopened consistently on desktop. Production drawing should follow phase 5 if it can update already calculated results; preview-only UI work can proceed independently once import compatibility is stable.

## 7. Add explicit ROBis start-list and final-result publishing

This can proceed independently of phases 5–6 after phase 2, because imported/existing start lists can be published without a new draw UI.

- Verify current provider endpoints, payloads, CSV columns, relative/absolute time conventions, response handling, and whether uploads replace existing remote data. Compare upstream fixtures against Radio-Oracle's existing dedicated ROBis exporter.
- Add shared request/payload helpers and reuse the current service worker/state machinery. Keep generic CSV formats stable; expose a provider-specific export where appropriate.
- Make start-list upload and final-result publication separate, clearly labeled actions. Show the intended race and operation. Preserve automatic live-result behavior and ensure the actions cannot upload a stale or mixed snapshot while data is changing.
- Handle unauthorized requests, network failures, rejected rows, timeouts, and retry/duplicate behavior according to the provider contract. A timeout is not proof that the server rejected the upload. Mark records successful only when acknowledged and keep credentials out of logs.

Validation: local request/response fixtures first, then a disposable ROBis playground race when the implementation scope includes external validation. Check both accepted and partial/error responses and verify remote content, rather than treating HTTP success alone as proof of correct results. Production publication remains an explicit operation.

Exit: file export, live upload, start-list upload, and final publication have distinct tested contracts; the intended remote data is verified; retry behavior is understood.

## Validation, delivery, and deferred work

Run focused tests while changing behavior. At completion of an Android batch use `just android-check`; for shared behavior also run the relevant shared tests through `just gradle :shared:desktopTest` and the affected desktop tests through existing wrappers. `just test` is the desktop suite, not a substitute for explicitly checking shared and Android tests. Run Gradle sequentially. Run the existing course/series transfer or IOF gates when changes affect those workflows; avoid unrelated full-release gates for isolated UI fixes.

Before any eventual installation/deployment, record the required standards outcome and test on representative Android hardware with copied data. Timing changes require a real SPORTident readout check; printing changes require paper inspection; provider publishing needs remote verification. Report unavailable hardware/service validation separately from software passes. Preserve recoverable app/data state and provide fresh process/device evidence for any requested install or launch.

Each implementation change should record the upstream source commit, Radio-Oracle adaptation, validation, and any remaining limitation. Integrate phase 1–2 as the first correctness candidate; phase 3 can follow in independent commits. Phases 4–7 remain separate feature scopes and should not hold up verified early fixes. Reassess the code and priority at each batch boundary rather than requesting routine permission for already authorized implementation work.

Defer the already surpassed SDK 36 upgrade, already handled adapter-position cleanup, replacement of the current ticket time-row formatter, and hiding the SI week field. Review the unused `isInLimit` helper only as targeted cleanup; it is not a demonstrated current display failure.
