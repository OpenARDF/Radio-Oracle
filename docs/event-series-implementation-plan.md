# Race Series Implementation Plan

## Summary

Race Series support is an opt-in championship and multi-day layer above existing `.rom.json` Race Files. Individual Race Files remain the source of truth for race-day operation. A series manifest inside the series folder owns membership, race order, and cross-race metadata. When the open Race File is tied to a series, Radio-Oracle exposes a contextual Series workflow for cross-race navigation, validation, export, competitor matching, and start fairness tools.

## Core Model

- Persist a lightweight `series.radio-oracle.json` manifest for each series.
- Treat the manifest as authoritative: folder contents alone do not define membership.
- Record series ID/name, ordered race entries, relative Race File paths, display names/dates/formats, and competitor-match overrides.
- Allow optional Race File `seriesLink` metadata with `seriesId` and `seriesEventId`.
- Use `seriesLink` only as recognition and safety metadata. The manifest remains the source of truth.
- Keep Race Files portable and independently usable outside a series.

## Desktop Workflow

- Manage current-race membership from Race File > Settings > Race Series.
- Support create, link, change link, remove link, and validate link actions.
- Update both the Race File backlink and the manifest when linking or unlinking, or fail clearly before creating a half-linked state.
- Show the Series bottom workflow only when the current Race File has a valid series context.
- Keep normal single-race workflows unchanged when no series context is active.
- Use the Series workflow for Races, Start Fairness, Competitor Matching, Series Validation, Series Settings, and Export Series.
- Opening another race in the series must use the same unsaved-change protection as loading any Race File.

## Cross-Race Behavior

- Load linked Race Files as read-only snapshots unless the user explicitly opens one as the current Race File.
- Track series dirty state separately from Race File dirty state.
- Match competitors across races by SI number, bib number, call sign, and operator-approved overrides.
- Report ambiguous, missing, duplicate, and conflicting matches without blocking ordinary Race File work.
- Reuse the existing balanced-thirds start-list algorithm for series-aware fairness.
- Derive history from other linked Race Files with generated starts for `Balance Open Race for Series`.
- Order series Races and Start Fairness histories by race date/time when all series races have usable dates, with stored manifest order as the fallback.
- Keep Race Series as the organizer-facing workflow for multi-day start fairness.

## Export And Documentation

- Add `Export Series...` to create a clean backup copy.
- Copy only `series.radio-oracle.json` and manifest-listed Race Files.
- Preserve relative paths where practical.
- Leave behind unrelated folder clutter such as drafts, backups, logs, and stale files.
- Validate that required Race Files exist before export.
- Maintain `docs/event-series.md`, `docs/start-list-draw.md`, and sample series data as the feature evolves.

## Testing And Acceptance

- Test manifest encode/decode, validation, unknown-field tolerance, duplicate membership rejection, and path safety.
- Test Race File `seriesLink` compatibility and ordinary Race Files without series metadata.
- Test desktop series create/open/save/close, dirty-state isolation, link/unlink, validation, and clean export.
- Test contextual Series workflow visibility and default single-race navigation behavior.
- Test competitor identity matching by SI number, bib number, call sign, and override.
- Test series-based start balancing against equivalent generated-start history.
- Acceptance scenarios:
  - A single-day race behaves exactly as before.
  - A linked championship race shows the Series workflow.
  - Extra Race Files in the folder are ignored unless listed in the manifest.
  - An open Race File can balance starts from other linked races with generated starts without selecting CSVs.
  - Export Series produces a clean backup folder without unrelated clutter.

## Deferred Scope

- Championship scoring, overall standings, point rules, eligibility rules, absent-result handling, and tie-break behavior are later work.
- Android remains focused on individual Race Files until a specific Android series workflow is planned.
