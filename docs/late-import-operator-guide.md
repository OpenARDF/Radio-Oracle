# Late Import Operator Guide

Radio-Oracle is designed to tolerate event data arriving late. Use this flow when controls, categories, competitors, courses, or results are corrected after the Event File has already been built.

## Before Importing

Open Event Diagnostics and check Event Readiness. Warnings about categories without competitors are informational; those categories are not treated as active in Race Ops or Results until competitors are assigned to them.

If a workflow menu is disabled only because setup is incomplete, you can long-click that disabled menu for 3 seconds to explore the page. The menu remains gray because the workflow rule is still unmet. This override is only for navigation menus, not action buttons that need valid data before they can run.

## Import Review

Use the import review dialogs for KML/KMZ, controls CSV, categories CSV, and competitors CSV. Review added, changed, skipped, and affected-course counts before applying the import.

If the import appears to describe a different event type than the Event File, treat the warning as a stop-and-check prompt. Continue only when the imported file is truly intended for this event.

## Rollback

Every applied import captures two rollback points:

- The in-app Restore Before Import button restores the immediate pre-import state for the current app session.
- A persistent `.rom.json` backup is written to `Application Support/Radio-Oracle/import-backups` and listed in the Recent Import report.

To recover later, open the backup Event File like any other Event File.

## Recalculate Results

After changing controls, category course assignments, competitor categories, or imported routes, use Event Diagnostics > Recalculate Results.

Recalculation re-evaluates stored readouts against the current course data, updates places, preserves status-only results such as DNS, and marks changed results unsent so they can be posted again.

## Automation Hooks

For regression checks and debugging:

```bash
./gradlew desktopApp:run --args="nav-availability /path/to/event.rom.json"
./gradlew desktopApp:run --args="recalculate-results --write /path/to/event.rom.json"
./gradlew desktopApp:run --args="readiness-summary --require-ready /path/to/event.rom.json"
```
