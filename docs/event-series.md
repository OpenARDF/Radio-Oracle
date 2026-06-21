# Event Series

Event Series support is an opt-in layer for championships and other multi-event competitions. Each `.rom.json` Event File remains the source of truth for one race-day event. A series manifest named `series.radio-oracle.json` records which Event Files belong to the series and in what order.

The manifest is authoritative. Radio-Oracle does not treat every `.rom.json` file in the folder as part of the series, so drafts, backups, logs, and other clutter can remain nearby without affecting cross-event tools.

Each linked Event File may also contain a `seriesLink` with the series ID and event ID. That backlink helps the desktop app recognize that the open Event File belongs to a series, but it does not define membership by itself. If the backlink and manifest disagree, validation reports the mismatch.

## Desktop Workflow

- Link or unlink the current Event File from Event File > Settings > Event Series.
- When the current Event File has a series link, the bottom workflow bar can show a contextual Series workflow.
- Series tools cover event navigation, start fairness, competitor matching, validation, series settings, and clean export.
- Opening another series event must use the same unsaved-change protection as loading any other Event File.

## Clean Export

`Export Series` creates a clean backup package by copying the manifest and only the Event Files listed in that manifest. It intentionally leaves behind unrelated files that may have collected in the working folder.

The export validates that required Event Files exist before writing the backup. Exported manifests keep their relative paths pointing at the copied Event Files.

For CLI smoke testing, use `just series-export <manifest> <target-folder>`. The command runs the same clean-export path and reports the copied manifest and Event File paths as JSON.

Use `just series-list <manifest> [current-event]` to inspect manifest-owned events, and `just series-add-event <manifest> <event-file>` to exercise the same add-event path used by the desktop Series workflow.

Use `just series-validate <manifest> '--require-clean'` when automation should fail if the manifest, required Event Files, backlinks, or cross-event checks report warnings or errors.

## Scope

The first implementation supports manifest storage, Event File backlinks, contextual navigation, clean export helpers, competitor matching diagnostics, and series-based start fairness inputs. Championship scoring and overall standings remain a later phase.
