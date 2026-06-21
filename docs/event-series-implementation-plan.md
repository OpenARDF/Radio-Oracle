# Event Series Implementation Plan

## Summary

Event Series support is an opt-in championship and multi-day layer above existing `.rom.json` Event Files. Individual Event Files remain the source of truth for race-day operation. A series manifest inside the series folder owns membership, event order, and cross-event metadata. When the open Event File is tied to a series, Radio-Oracle exposes a contextual Series workflow for cross-event navigation, validation, export, competitor matching, and start fairness tools.

## Core Model

- Persist a lightweight `series.radio-oracle.json` manifest for each series.
- Treat the manifest as authoritative: folder contents alone do not define membership.
- Record series ID/name, ordered event entries, relative Event File paths, display names/dates/formats, and competitor-match overrides.
- Allow optional Event File `seriesLink` metadata with `seriesId` and `seriesEventId`.
- Use `seriesLink` only as recognition and safety metadata. The manifest remains the source of truth.
- Keep Event Files portable and independently usable outside a series.

## Desktop Workflow

- Manage current-event membership from Event File > Settings > Event Series.
- Support create, link, change link, remove link, and validate link actions.
- Update both the Event File backlink and the manifest when linking or unlinking, or fail clearly before creating a half-linked state.
- Show the Series bottom workflow only when the current Event File has a valid series context.
- Keep normal single-event workflows unchanged when no series context is active.
- Use the Series workflow for Events, Start Fairness, Competitor Matching, Series Validation, Series Settings, and Export Series.
- Opening another event in the series must use the same unsaved-change protection as loading any Event File.

## Cross-Event Behavior

- Load linked Event Files as read-only snapshots unless the user explicitly opens one as the current Event File.
- Track series dirty state separately from Event File dirty state.
- Match competitors across events by SI number, bib number, call sign, and operator-approved overrides.
- Report ambiguous, missing, duplicate, and conflicting matches without blocking ordinary Event File work.
- Reuse the existing balanced-thirds start-list algorithm for series-aware fairness.
- Derive history from other linked Event Files with generated starts for `Balance Open Event for Series`.
- Order series Events and Start Fairness histories by event date/time when all series events have usable dates, with stored manifest order as the fallback.
- Keep Event Series as the organizer-facing workflow for multi-day start fairness.

## Export And Documentation

- Add `Export Series...` to create a clean backup copy.
- Copy only `series.radio-oracle.json` and manifest-listed Event Files.
- Preserve relative paths where practical.
- Leave behind unrelated folder clutter such as drafts, backups, logs, and stale files.
- Validate that required Event Files exist before export.
- Maintain `docs/event-series.md`, `docs/start-list-draw.md`, and sample series data as the feature evolves.

## Testing And Acceptance

- Test manifest encode/decode, validation, unknown-field tolerance, duplicate membership rejection, and path safety.
- Test Event File `seriesLink` compatibility and ordinary Event Files without series metadata.
- Test desktop series create/open/save/close, dirty-state isolation, link/unlink, validation, and clean export.
- Test contextual Series workflow visibility and default single-event navigation behavior.
- Test competitor identity matching by SI number, bib number, call sign, and override.
- Test series-based start balancing against equivalent generated-start history.
- Acceptance scenarios:
  - A single-day event behaves exactly as before.
  - A linked championship event shows the Series workflow.
  - Extra Event Files in the folder are ignored unless listed in the manifest.
  - An open Event File can balance starts from other linked events with generated starts without selecting CSVs.
  - Export Series produces a clean backup folder without unrelated clutter.

## Deferred Scope

- Championship scoring, overall standings, point rules, eligibility rules, absent-result handling, and tie-break behavior are later work.
- Android remains focused on individual Event Files until a specific Android series workflow is planned.
