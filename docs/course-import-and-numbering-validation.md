# Course import and numbering validation

Completed September 16, 2026 (America/New_York), against the working changes based on `b7a4b066` / v1.0.47. Local desktop test build: **1.0.47e**. This records pre-release validation; release authorization and the additional-test waiver are recorded in [the 1.0.48 release notes](release-notes-1.0.48.md).

## Implemented behavior

- IOF reimport preserves an existing Beacon or Spectator role when an unnamed ordinary XML control does not specify an ARDF role. Assignments and map locations use the same resolved role. Explicit conflicts stop with a correction message. Import acceptance remains a separate transaction.
- **Setup → Categories → Assign Existing Course…** reuses either an assigned or unassigned course for multiple categories. It retains competitors, source categories, station numbers, course geometry and metadata, including password protection. Controls-only courses are supported and labeled when they lack locations. Course Analyzer remains optional.
- **Apply Calculated Course** preserves numbering. **Review Fox Renumbering…** shows old/new fox and SI station assignments and affected courses, then requires explicit confirmation. Cancel preserves the race. Recorded activity and stale calculations remain protected by the existing checks.
- Practice category fox-count limits are advisory. Missing beacons and other consistency errors remain blocking. Other race levels retain their normal validation.
- PDF shared exclusion warnings and wrapped continuation lines are red. The export button says **Export PDF and KML…**. Setup, report recommendations, and control-use messages describe the current actions.
- An optional, initially unchecked IOF setting interprets Condes numbers 900–999 as non-scoring route points. It reuses mandatory-waypoint handling on matching legs, including reversed legs. Missing coordinates, repeated point numbers within a course, and explicit special roles stop conversion. Normal imports still treat these numbers as SI controls.
- Diagnostic logs distinguish analysis, import preparation/acceptance/rejection, course assignment, normal application, and confirmed renumbering. Reproduction and UI hooks cover the new paths.

## Verification

| Check | Result |
| --- | --- |
| Full shared suite | 681 passed |
| Full desktop suite | 1,031 passed; 4 optional cases skipped in that run |
| Full Android unit suite | 316 passed; 1 optional transfer case skipped in that run |
| Explicit desktop → Android Room → desktop transfer | Passed for plain and protected archives, applied designs, and original punches; both transfer cases skipped above were exercised successfully |
| Supplied XML/Race File replay | Passed: four categories, two unique reports, SI-to-location identity retained; analysis/export left the race and both source files unchanged |
| Packaged desktop code replay | Passed against 1.0.47e: original XML accepted, beacon retained, four categories/two reports, new application actions present, source files unchanged |
| Desktop package/runtime/signature verification | Passed |
| Android debug package | Built successfully |
| UI/PDF visual review | Category assignment and renumbering dialogs inspected; exclusion-warning color, wrapping, and revised recommendation inspected |
| Diff whitespace check | Passed |

The supplied-file replay uses deterministic flat test elevations. Its generated reports are test artifacts, not terrain estimates. Synthetic tests separately cover route lengths, mandatory bends, renumbering, encryption, stale state, cancellation, and recorded-activity restrictions. Three unrelated opt-in archive/attachment tests remain skipped because their private fixture inputs were not configured. No Windows UI session or physical SPORTident/device bench test was performed.

The historical action that produced the earlier station exchange cannot be established from the old logs. The implementation removes the ambiguous default action and adds evidence for future investigations.

## Local artifacts and repeatable checks

- Desktop app: `desktopApp/build/compose/binaries/main/app/Radio-Oracle.app`
- Android debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Full-suite counts: `desktopApp/build/reports/course-import-validation/full-suite-counts.json`
- Supplied-file verification and PDF/KML reports: `desktopApp/build/reports/iof-reimport-acceptance/`
- Transfer result: `desktopApp/build/reports/course-workflow/round-trip.json`
- UI previews: `desktopApp/build/reports/categories-assign-existing-course.png` and `desktopApp/build/reports/course-renumbering-review.png`
- PDF warning fixture: `desktopApp/build/reports/exclusion-warning.pdf`

Use `just gradle :shared:desktopTest :desktopApp:test :app:testDebugUnitTest` for full unit coverage, `just course-workflow-transfer-test` for the dependent archive round trip, and `just desktop-package` for local packaging. The private-file replay properties and its isolation guarantees are documented in [Course Analyzer](course-analyzer.md#replaying-a-supplied-iof-regression). Private attachments were not added to the repository.
