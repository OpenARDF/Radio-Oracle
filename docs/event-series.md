# Race Series

Race Series support is an opt-in layer for championships and other multi-race competitions. Each `.rom.json` Race File remains the source of truth for one race-day race. A series manifest named `series.radio-oracle.json` records which Race Files belong to the series and in what order.

The manifest is authoritative. Radio-Oracle does not treat every `.rom.json` file in the folder as part of the series, so drafts, backups, logs, and other clutter can remain nearby without affecting cross-race tools. The Series name also belongs only to the manifest; linked Race Files do not store their own copies of that name.

Each linked Race File may also contain a `seriesLink` with the series ID and race ID. That backlink helps the desktop app recognize that the open Race File belongs to a series, but it does not define membership by itself. If the backlink and manifest disagree, validation reports the mismatch.

## Desktop Workflow

- Link or unlink the current Race File from Race File > Settings > Race Series.
- When the current Race File has a series link, the bottom workflow bar can show a contextual Series workflow.
- Series tools cover race navigation, start fairness, competitor matching, validation, series settings, and clean export.
- Edit the Series name from Race Series > Series Settings. Opening any member Race File resolves and displays the current manifest name.
- Opening another series race must use the same unsaved-change protection as loading any other Race File.

## Clean Export

`Export Series` creates a clean backup package by copying the manifest and only the Race Files listed in that manifest. It intentionally leaves behind unrelated files that may have collected in the working folder.

The export validates that required Race Files exist before writing the backup. Exported manifests keep their relative paths pointing at the copied Race Files.

For CLI smoke testing, use `just series-export <manifest> <target-folder>`. The command runs the same clean-export path and reports the copied manifest and Race File paths as JSON.

Use `just series-list <manifest> [current-race]` to inspect manifest-owned races, and `just series-add-event <manifest> <race-file>` to exercise the same add-race path used by the desktop Series workflow.

Use `just series-validate <manifest> '--require-clean'` when automation should fail if the manifest, required Race Files, backlinks, or cross-race checks report warnings or errors.

## Scope

The first implementation supports manifest storage, Race File backlinks, contextual navigation, clean export helpers, competitor matching diagnostics, and series-based start fairness inputs. Championship scoring and overall standings remain a later phase.
