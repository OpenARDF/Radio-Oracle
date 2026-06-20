# Start List Draw Algorithm

This document describes the shared start-list generator used by the desktop Start List screen. The implementation lives in `shared/src/commonMain/kotlin/org/openardf/radiooracle/shared/event/EventProjectEditor.kt`; persisted settings are modeled in `StartDrawOptions.kt`; grading and color-coded findings are modeled in `EventStartListDetails.kt`.

## Goals

The generator assigns relative start times to competitors while balancing hard limits and best-practice spacing:

- Do not exceed the configured number of starters per start time.
- Avoid multiple competitors from the same category at the same start time.
- Avoid consecutive competitors from the same category when another category is available.
- Avoid same-club starts when `Avoid same club` is enabled.
- Avoid same first-fox conflicts for similarly fast categories when protected course-order data is available.
- Honor assigned championship start thirds when `Preferred thirds` mode is enabled.
- Balance start thirds across multiple competition days when prior starts CSVs are supplied.
- Make non-default seeded draws repeatable across machines and runs.

Some events do not have enough categories or clubs to satisfy every best practice. In that case the generator completes the draw, and the evaluator reports the remaining compromises as orange or red findings.

## Persisted Settings

Start List settings are stored in the event project data, not desktop-local preferences. The persisted values are:

- `intervalSeconds`
- `clubHandling`
- `startersPerStartTime`
- `seed`
- `startGroupMode`

The default seed is `default`. That value is visible and persisted, but it preserves deterministic category/start-number ordering. Any non-default seed activates repeatable pseudo-random tie-breaking. Blank seeds are normalized back to `default` before settings are saved or a draw is run.

`startGroupMode` defaults to `No start groups`. In that mode, no start-third rule is applied and the generator behaves like a normal single-event draw. When changed to `Preferred thirds`, the generator uses each competitor's optional `preferredStartGroup` value. The canonical competitor CSV column is `preferred_start_group`; blank means no assignment, and accepted values are `1`, `2`, and `3`.

`Balanced thirds` is selected automatically when the desktop Start List panel's `Balance from CSVs` action is used. It derives current-day preferred thirds from one or more prior starts CSV files, saves those assignments into the current Event File, then runs the same constrained draw as `Preferred thirds`.

## Draw Model

The draw uses category queues:

1. Categories are collected in category order.
2. Competitors inside each category are ordered by the selected mode:
   - Default seed: start number, then full name.
   - Non-default seed: stable seed hash, then start number and full name.
3. When club avoidance is enabled, competitors within a category are first grouped into club queues. The largest club queues are drained first to spread large clubs across the category.
4. Each start time is filled up to `startersPerStartTime`.
5. For each position in a start time, the algorithm picks the best compatible category queue.

## Queue Selection Rules

For a start slot, queue selection applies filters in this order:

1. Remove categories already selected for the same start time.
2. Remove same-first-fox conflicts among similarly fast categories when protected ideal-order data supplies first foxes.
3. In `Preferred thirds` or `Balanced thirds` mode, prefer queues that still contain a competitor allowed in the current third of the start period.
4. Prefer a category different from the category that ended the previous start time.
5. If club avoidance is enabled, reject queues whose next eligible competitor would duplicate a club already selected for this same start time.
6. If club avoidance is enabled, prefer a queue whose next eligible competitor differs from the club that ended the previous start time.
7. Apply deterministic or seeded tie-breakers to the remaining queues.

Same-category adjacency and same-club adjacency across start times are best-practice filters. If every remaining queue violates one of those preferences, the algorithm falls back and continues the draw. Same-club duplication inside the same start time is stricter: the algorithm leaves the start time partially filled rather than knowingly adding a duplicate-club starter to that same slot.

## Competitor Selection Within a Queue

Most selection happens at category level, but club conflicts are competitor-specific. After a category queue is selected, the algorithm may look past the first competitor in that category queue to find a later competitor whose club avoids the current slot and previous-slot club conflicts. This preserves the category choice while improving club spacing.

In `Preferred thirds` and `Balanced thirds` mode, this same look-ahead first narrows the queue to competitors allowed in the current start third. A competitor with no preferred third may fill any third. If the remaining field makes the current third impossible, the draw falls back and completes; the quality evaluator then flags assigned competitors outside their preferred third as red findings.

## Championship Preferred Thirds

The championship procedure in the ARDF rules allows teams or societies to distribute runners across three start thirds. Radio-Oracle supports the computer-usable part of that process by storing an optional preferred third on each competitor and honoring it during the draw when `Preferred thirds` mode is enabled.

This support is intentionally limited to explicit preferred-third assignments. The separate ARDF procedure for categories with more than 40 competitors requires historical results from the last two championships to split societies into top-runner and other-runner ballots. Radio-Oracle does not currently store that historical-results input, so that top-runner split is not inferred by the generator.

## Multi-Day Balanced Thirds

Many championships have four separate competitions on consecutive days. Fairness across the series matters because the start thirds are not equally desirable: the middle third is usually best, the late third is next, and the early third is least desirable. A fair series should avoid assigning a competitor to the same third more than twice over four days, and should avoid giving one competitor only middle and late starts for the entire series when alternatives exist.

Radio-Oracle supports this with two desktop workflows:

- `Balance from Event Series` reads prior starts directly from earlier Event Files listed in the open Event Series manifest.
- `Balance from CSVs` remains the manual fallback when the series manifest is not available or prior starts arrive as exported CSV files.

For series-based balancing:

1. Link the Event File to an Event Series from Event File > Settings > Event Series.
2. Open the Event File for the next day.
3. Use the contextual Series workflow or Start List area to select `Balance from Event Series`.
4. Radio-Oracle reads only prior events listed earlier in the manifest order, computes preferred thirds, saves those assignments, and draws the start list in `Balanced thirds` mode.

For CSV-based balancing:

1. Draw and export the starts CSV for each completed event day.
2. Open the Event File for the next day.
3. Select `Balance from CSVs`.
4. Select the prior starts CSV files for the same multi-day competition.
5. Radio-Oracle computes a preferred third for every current competitor, saves those assignments, and draws the start list in `Balanced thirds` mode.

Prior starts are matched to current competitors by SI number when present. If no SI number is available, start number is used as a fallback. This fallback is less reliable when start numbers change between days, so SI numbers are preferred for multi-day fairness. Series-based matching can also use operator-approved competitor match overrides stored in the series manifest when the same person cannot be matched confidently from event data alone.

Each selected prior starts CSV, or each prior Event File selected by the series manifest, is converted into first, middle, and late thirds by sorting its distinct start times and applying the same third-boundary rule used by the draw. The current-day assignment heuristic then evaluates each competitor's history:

- A third already used twice by that competitor receives a large penalty.
- If the competitor has three prior starts and none were early, non-early choices receive a large penalty.
- Current-day capacity is respected so the generated preferred thirds fit the available start slots.
- Current-day group counts are kept balanced.
- Remaining ties account for desirability, then the configured seed.

The algorithm is a deterministic heuristic, not an exhaustive optimizer. It is intended to produce reviewable, fair assignments when participant lists differ between days. If the field is too constrained, the draw still completes and the quality evaluator flags any saved current-day start-third violations in red.

The goodness factor evaluates whether the saved start order honored the balanced assignments produced by `Balance from Event Series` or `Balance from CSVs`. It does not re-read the prior Event Files or CSV files during scoring, and it does not separately prove that the balanced assignment was globally optimal across every day. In balanced mode, the multi-day fairness work happens when Radio-Oracle computes the current-day preferred thirds; the quality score then verifies that the final drawn start order stayed inside those computed thirds.

## Seeded Randomization

Seeded randomization uses a stable hash function instead of platform random APIs. This is intentional: a given seed should produce the same start order across supported platforms and future runtime versions.

The seed only breaks flexible choices. It does not bypass rule filters. For example, a seeded draw can change which compatible category is selected first, but it still avoids same-category same-time starts when another compatible category exists.

## Quality Evaluation

`EventStartListQuality` grades the saved start times without re-running the draw. That means generated, manually edited, and imported start lists are all evaluated by the same code.

Severity meanings:

- Green: no detected rules violations or best-practice compromises.
- Orange: start-order rules are met, but the list is not ideal.
- Red: a hard rule or saved capacity setting is violated.

The score is a weighted summary from 1 to 100. It is a practical goodness factor, not a mathematical proof of optimality. Reviewers should use the score together with the emitted messages and row findings.
