# Course workflow implementation evidence

Candidate in progress, 2026-09-06, based on `e4f6133a` on `Development1`. This supersedes the initial characterization checkpoint. The final release gates below are still being completed; this is not a claim of full acceptance.

## Delivered responsibilities

| Slice | Implemented and exercised | Acceptance boundary |
| --- | --- | --- |
| 0: reproducible workflow | Production import, three KML export/re-import/save/reopen iterations, Analyzer, Apply, venue redesign, synthetic SPORTident downloads, historical-copy refusal/recovery, local series replacement; per-step JSON | One complete Classic lifecycle; additional format cases at the service boundary |
| 1: identity | Shared explicit placement/control/SI bindings, complete ordered visits, semantic revision, actionable legacy ambiguity; schema 7 | Exhaustive Classic permutations/subsets, opaque IDs, role conflicts and invalid explicit bindings |
| 2: drafts | Draft persisted within Race File, separate applied result state and unlocked caches; imports, point movement, ideal order, numbering and speed/elevation edits use candidate state; cancel/save/reopen | Unfinished drafts cannot replace scoring assignments; movement invalidates dependent routes/orders/metrics including inactive mappings |
| 3: Apply | One prepare/validate/commit service; real Compose station review with coordinates; every required course calculated with accepted numbering; recorded races offer revised copy | Stale calculations, conflicting positions, missing courses, ambiguous mappings and recorded activity block commit; encrypted identical reapply preserves stored payloads |
| 4: persistence | Shared protection covers active/inactive/draft payloads; Room 12→13 preserves portable identities; checked whole-archive writes and legacy external-member rollback journal | Failure injection, interrupted recovery, external edits, wrong passwords; desktop-generated archive passes through Android Room and back with applied courses and punches |
| 5: outputs | Shared resolved projection feeds applied Analyzer, route analysis, KML/GPX, IOF CourseData and public diagrams; canonical SI descriptions remove stale imported hints | Independent known station/position oracle across KML/GPX/XML/SVG; Classic 2m, Sprint and Foxoring Apply checks |
| 6: publication | Shared checked staging on desktop/Android; complete selected-course generation; obsolete targeted artifacts removed; frozen public inventory and page-link validation; binary fresh-download verification after upload | Mock publisher and missing/corrupt/stale artifact tests; previous local mirror retained on pre-promotion failure; real deployment gate remains open |
| 7: automation/operator guidance | Just recipes, CLI preview/audit/export/publication verification, Compose UI tests, isolated app-data/preferences, PR/manual CI workflow, updated Analyzer documentation and lock tooltip | Final broad tests, packaged Mac, device, real-archive and controlled deployment evidence below |

## Reuse and cleanup

- Removed the old calculated-route mutation implementation and its result DTO; its callers use the reviewed prepare/commit service.
- Moved legacy field-label and importer-ID compatibility helpers into shared code; removed their desktop duplicates.
- Shared placement validation and explicit resolution replace independent identity guessing at migrated consumers. Invalid explicit bindings never silently fall back to legacy IDs.
- Moved SI description parsing to shared code. Applied exports derive SI descriptions from the reviewed station binding.
- Reused existing route samplers, terrain providers, Analyzer searches, shared scoring/readout rules, KML/GPX/IOF/PDF writers, file/archive codecs and Room adapters.
- Extracted the checked desktop site-staging implementation for Android reuse; removed Android's destructive preparation/reset path and the duplicate desktop staging internals.
- Kept compatibility adapters for supported legacy files and the numbering proposal transformer used by drafts. They are not dead code.
- Preserved the original mixed-worktree patch and untracked files under `/tmp/radio-oracle-course-workflow-baseline` before implementation.

## Supported commands

- `just course-workflow-test`: desktop lifecycle and required per-step report.
- `just course-workflow-transfer-test`: dependent desktop fixture → Android Room → returned desktop archive checks, plaintext and encrypted.
- `just course-workflow-check`: shared/course/UI/persistence/publication/Android regressions plus the transfer acceptance pipeline.
- `just course-audit <race-or-series>`: read-only identity, stale-data and coverage audit; locked data is blocked.
- `just course-apply-preview <race-or-series> <design.json>`: production preparation with explicitly reviewed station bindings, without source writes.
- `just course-export-verify <race-or-series> <new-output-directory>`: production KML, GPX, IOF XML and SVG plus output checks.
- `just course-publication-manifest <site> <new-inventory.json>`: validate public files/links and record exact public-byte inventory outside the site.
- `just course-publication-verify <https-site-root> <inventory.json>`: fresh downloads compared with the frozen inventory.

Preview/export CLI hooks require plaintext course inputs. Use the application for protected inputs. No CLI accepts passwords or tokens in arguments. The design JSON format and operational workflow are documented in `docs/course-analyzer.md`.

Report validation returns 0 only when every requested report step passed; failed, blocked and skipped steps return 1. Invalid arguments/unreadable reports return 2. Preview/export failures return 1 after valid argument counts; bad argument counts return 2. Publication inventory creation errors return 2; download mismatches return 1.

## Evidence generated locally

- `desktopApp/build/reports/course-workflow/baseline.json`: 12 desktop lifecycle steps passed.
- `app/build/reports/course-workflow/android-transfer.json`: both portable identity/protection checks passed.
- `desktopApp/build/reports/course-workflow/round-trip.json`: plaintext, encrypted, and applied-design/raw-punch comparisons passed after real Android Room persistence under Robolectric.
- Module `build/test-results` and `build/reports/tests` directories contain the JUnit evidence.
- Classic identity coverage includes 120 permutations × 31 nonempty subsets for legacy identity and separately for explicit bindings.
- Actual Compose review tests cover Prepare/Apply/save/reopen and Cancel; service tests cover stale calculations and incomplete review coverage.
- Local replacement tests preserve prior bytes on failed generation, reject external mirror edits, and remove obsolete diagrams/download links. The new link validator exposed and fixed stale download links on Coming Soon pages.
- Mock upload tests verify that public downloads omit authorization headers and use fresh cache-bypass requests. Binary inventory tests reject corrupted or missing graphics and files changed after selection.

## Release gates still open

The aggregate workflow suite and final full suites passed: 661 shared tests, 872 desktop tests, and 255 Android tests, with zero failures. The ordinary full-suite run skipped 3 desktop and 1 Android opt-in cases; the archive and transfer hooks were executed separately and passed. The independent IOF schema gate passed. The actual last-opened championship archive passed retained-terrain reproduction (55 saved estimates, 9 exact ideal-order matches) and the four selected diagram checks on a read-only copy. The calculation method is now v3 so legacy fingerprints refresh after schema-7 binding fields were introduced. Packaged Mac UI, current Android device execution, and a controlled real deployment/fresh-download comparison remain open. CI configuration exists but has not been executed remotely. A synthetic SPORTident stream verifies software scoring only; physical station/card proof is separate.

Broader stress coverage is still narrower than the entire proposed combinatorial matrix: the cross-platform lifecycle uses a Classic fixture, with Sprint/Foxoring application and identity cases tested separately. No claim is made that every combination of format, protection, device, terrain and deployment failure has been exercised together.

The file format deliberately advances to schema 7 while retaining reads of schemas 1–6. Older schema-6 clients must not edit new files. The Android database migration adds nullable portable metadata without changing legacy row identities. No production archive has been bulk rewritten by the acceptance tests.

Deployment acceptance and public verification are separate outcomes. If Cloudflare accepts a deployment but public-byte verification fails, the app reports an incomplete publication and retains the previous local mirror. The provider may already have replaced its public deployment; automatic provider rollback is not claimed.

The Moto device check is prepared as `just android-course-workflow-smoke <serial>`. Its debug-only receiver imports the synthetic archive with fresh UUIDs, exports both protection modes, and removes only the races created by that invocation. Device installation is paused pending approval for a private pre-upgrade database/preferences backup; automatic approval review rejected that payload transfer. No device data was copied or changed by the rejected command.
