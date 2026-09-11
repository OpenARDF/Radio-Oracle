# Scrolling audit — 2026-09-11

Joseph's controls/route import report exposed incomplete adoption of the shared
desktop scrolling behavior. The import review retained a separate wheel-only
body without a visible scrollbar or the workspace's keyboard handling. Its
layout dates to June; history does not establish a new reversion in 1.0.45.

## Coverage and changes

| Surface | Audit result |
| --- | --- |
| 49 desktop Material alert call sites | Migrated to `DesktopAlertDialog`: a bounded scrolling body, fixed title, and wrapping action row. Covers warnings, imports, reports, settings, previews, and progress messages. Short alerts still wrap their content. |
| Five custom desktop dialogs | Controls/route review, spreadsheet setup/review, and readout edit now use `DesktopWorkspaceScroll`; course application already used it. Dynamic source names and warnings belong inside the viewport. |
| Desktop workspace and navigation | Reused the common visible scrollbar and keyboard handling. Variable navigation actions now scroll with the menu; Save and Back remain fixed. |
| Native desktop dialogs | Wrapped variable-length readout issue notices in the same scrollable text presentation as starts-CSV bib reviews. File choosers and short native confirmations retain their existing native behavior. |
| All 16 custom Android dialog XML layouts | Added scrolling to the eight layouts that lacked whole-form reachability: About, assign controls, delete category/competitor, readout edit, internet import, share results, and standard categories. The other eight already had scroll containers. |
| Android programmatic dialogs | Diagnostic-log actions now scroll. Race protection already used a scroll view; single-input and native message/list dialogs retain framework behavior. |
| Android readout details | The header and punch list scroll together, with the toolbar fixed. Readout editing keeps Save/Cancel fixed. Nested punch-list scrolling is disabled so the entire bounded form has one viewport. |
| Other Android lists and generated public HTML | Reviewed native list containers and generated overflow styles. No additional fixed-height clipping of growing content was identified in these paths. |

## Regression protection

- `DesktopCourseImportReviewScrollTest`: four cases exercising the actual import
  review at 100% and 150% display scaling. Checks Page Down, Home/End, wheel,
  scrollbar dragging, the final notice, and pinned Cancel/Accept Import actions.
  Captures top/bottom images under `desktopApp/build/reports/import-review/`.
- `DesktopAlertDialogScrollTest`: twelve cases at 100%, 150%, and 200% font
  scaling in 320- and 520-dp-high dialogs. Exercises growing warning content,
  keyboard navigation while editing, unchanged field text, visible final
  fields, stable actions, cancel callbacks, and compact short alerts.
- `DesktopScrollArchitectureTest`: rejects new raw Material alerts, standalone
  vertical scrolling, and unaudited custom desktop dialog call sites.
- Existing `DesktopWorkspaceScrollTest`: three common workspace input cases.
- `DialogScrollLayoutTest`: four Robolectric cases. Discovers all custom XML
  dialogs and checks finite usable scroll viewports, then checks long errors,
  final controls, forty readout punches, and fixed readout actions/toolbars at
  a 360-by-240 viewport.

Run the full validation with:

```sh
just gradle :desktopApp:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
git diff --check
just function-size
```

Desktop UI tests run through Compose on macOS; Android layout tests run through
Robolectric. These checks do not constitute a physical Windows or Android device
test. Native popup menus/file pickers and unbounded RecyclerView/table lists keep
their platform scrolling behavior rather than acquiring nested form scrollbars.

Validation on 2026-09-11: the full desktop suite reported 940 tests (four optional
fixture-dependent skips), and the full Android suite reported 316 tests (one
optional transfer-fixture skip), with no failures or errors. Both the Android
application and instrumented-test APKs built successfully. `git diff --check`
passed. The function-size audit continues to report existing oversized functions;
the new shared dialog and test functions are below its 150-line threshold.
