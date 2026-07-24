# Race Series

Race Series support is an opt-in layer for championships and other multi-race competitions. New series are stored as one `.roseries` Radio-Oracle Series File. The file is a ZIP-compatible container holding `series.radio-oracle.json` and one JSON Race File for every member.

The manifest inside the container is authoritative. Radio-Oracle does not infer membership from other files near the container. The Series name also belongs only to the manifest; linked Race Files do not store their own copies of that name.

Each linked Race File may also contain a `seriesLink` with the series ID and race ID. That backlink helps the desktop app recognize that the open Race File belongs to a series, but it does not define membership by itself. If the backlink and manifest disagree, validation reports the mismatch.

Existing folder-based series using `*.series.radio-oracle.json` plus standalone Race Files remain readable and exportable for compatibility.

## Desktop Workflow

- Creating a series writes a `.roseries` file and opens its contained Race File. The original standalone Race File is retained as a safety copy.
- Adding a standalone Race File copies it into the `.roseries` container. The source file remains unchanged.
- Removing a race requires a standalone destination. Radio-Oracle writes and verifies that file before rewriting the container without the member.
- Link or unlink the current Race File from Race File > Settings > Race Series.
- When the current Race File has a series link, the bottom workflow bar can show a contextual Series workflow.
- Series tools cover race navigation, start fairness, competitor matching, validation, series settings, and clean export.
- Edit the Series name from Race Series > Series Settings. Opening any member Race File resolves and displays the current manifest name.
- Opening another series race must use the same unsaved-change protection as loading any other Race File.

## Clean Export

`Export Series` can create a legacy folder copy containing the manifest and only the Race Files listed in that manifest. Saving or transferring a Radio-Oracle Series File uses the `.roseries` container itself.

The export validates that required Race Files exist before writing the backup. Exported manifests keep their relative paths pointing at the copied Race Files.

## Persistence and recovery

Desktop saves rewrite the complete container to a temporary sibling, flush it, and atomically replace the previous `.roseries` file. Radio-Oracle checks the archive fingerprint before saving and refuses to overwrite a container changed by another application.

Android uses the same shared archive aggregate and ZIP codec. It imports a `.roseries` document into its transactional Room working store and reconstructs the same container format for file export or desktop transfer. Android also registers the `.roseries` MIME type so a selected Series File can be opened directly.

For CLI smoke testing, use `just series-export <manifest> <target-folder>`. The command runs the same clean-export path and reports the copied manifest and Race File paths as JSON.

Use `just series-list <manifest> [current-race]` to inspect manifest-owned races, and `just series-add-event <manifest> <race-file>` to exercise the same add-race path used by the desktop Series workflow.

Use `just series-validate <manifest> '--require-clean'` when automation should fail if the manifest, required Race Files, backlinks, or cross-race checks report warnings or errors.

## Scope

The first implementation supports manifest storage, Race File backlinks, contextual navigation, clean export helpers, competitor matching diagnostics, and series-based start fairness inputs. Championship scoring and overall standings remain a later phase.
