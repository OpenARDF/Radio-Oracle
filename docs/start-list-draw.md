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
- Balance start thirds across multiple competition days through the Race Series tools.
- Make non-default seeded draws repeatable across machines and runs.

Some races do not have enough categories or clubs to satisfy every best practice. In that case the generator completes the draw, and the evaluator reports the remaining compromises as orange or red findings.

## Persisted Settings

Start List settings are stored in the race project data, not desktop-local preferences. The persisted values are:

- `intervalSeconds`
- `clubHandling`
- `startersPerStartTime`
- `seed`
- `startGroupMode`
- `lockedForSeriesOptimization`

The seed is persisted for compatibility and repeatable internal generation, but it is not shown on the desktop Start List screen. The desktop `Generate Start List` button supplies a hidden non-default seed on each press and tries to find a start order that has not already been generated for that Race File during the current desktop session. Radio-Oracle numbers distinct generated start orders and shows the current start order number next to the button. If the race constraints do not produce another unique order, the draw returns to start order #1.

`startGroupMode` defaults to `No start groups`. In that mode, no start-third rule is applied and the generator behaves like a normal single-race draw. When changed to `Preferred thirds`, the generator uses each competitor's optional `preferredStartGroup` value. The canonical competitor CSV column is `preferred_start_group`; blank means no assignment, and accepted values are `1`, `2`, and `3`.

`Balanced thirds` is an internal mode used by series balancing tools. It derives current-day preferred thirds from other series Race Files with generated starts, saves those assignments into the current Race File, then runs the same constrained draw as `Preferred thirds`.

`lockedForSeriesOptimization` is set from the desktop Start List checkbox labeled `Lock this start list`. When checked, the Start List screen disables start-list controls that would redraw or reinterpret the current order: interval, club handling, starters per time, start-group mode, and `Generate Start List`. The Series optimizer also skips locked Race Files and reports how many unlocked Race Files remain available for optimization.

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

Radio-Oracle supports this with series-aware desktop workflows:

- `Balance Open Race for Series` redraws only the open Race File, using other series Race Files that already have generated starts.
- `Optimize Series Starts` searches for improved randomized start-list combinations across the series.

`Optimize Series Starts` can be pressed repeatedly to look for alternate randomized solutions. Radio-Oracle numbers distinct whole-series start assignments found during the current desktop session and reports when a press repeats an earlier solution.

If a Race File's Start List page has `Lock this start list` checked, `Optimize Series Starts` leaves that Race File's generated starts unchanged and searches only through the unlocked races. This supports manual decisions such as keeping one race fixed while improving fairness with the remaining days.

The Start Fairness panel reports a 0-100 fairness number. A score of 100 means every identified competitor with at least two generated starts is balanced as well as mathematically possible across early, middle, and late thirds. For example, one start in each third is perfect across three races, and a 2/1/1 split is perfect across four races. If no identified competitor has enough generated starts to score, the fairness number is 0 until more start history exists.

If the score is high enough, the panel reports that no optimization is needed. If optimization has already been attempted and no better result was found, a low score reports that manual start-parameter review may be needed. Possible interventions include changing one or more race start intervals, changing competitors per start time, or inserting empty starts before optimizing again.

For a Race File linked to a series, the Start List page shows both `Race Start Fairness Score` and `Series Start Fairness Score`. The series score uses the open in-memory Race File for the current race, so manual start-time edits are reflected before the Race File is saved. Other series races still contribute from their saved Race Files.

The Series Races list and the Start Fairness History column are ordered by race date/time when every series race has a usable date. If any series race is missing a date or has an invalid date, Radio-Oracle falls back to the stored series order.

For series-based balancing:

1. Link the Race File to a Race Series from Race File > Settings > Race Series.
2. Open the Race File for the next day.
3. Use the contextual Series workflow to select `Balance Open Race for Series`.
4. Radio-Oracle reads other series races that already have generated starts, computes preferred thirds, saves those assignments, and draws the open race's start list in `Balanced thirds` mode.

Prior starts from linked Race Files are matched to current competitors by persistent identity fields: SI number, bib number, then call sign. Start/order numbers are not used as competitor identity keys.

Each other generated-start Race File selected by the series manifest is converted into first, middle, and late thirds by sorting its distinct start times and applying the same third-boundary rule used by the draw. The current-day assignment heuristic then evaluates each competitor's history:

- A third already used twice by that competitor receives a large penalty.
- If the competitor has three prior starts and none were early, non-early choices receive a large penalty.
- Current-day capacity is respected so the generated preferred thirds fit the available start slots.
- Current-day group counts are kept balanced.
- Remaining ties account for desirability, then the configured seed.

The algorithm is a deterministic heuristic, not an exhaustive optimizer. It is intended to produce reviewable, fair assignments when participant lists differ between days. If the field is too constrained, the draw still completes and the quality evaluator flags any saved current-day start-third violations in red.

The goodness factor evaluates whether the saved start order honored the balanced assignments produced by `Balance Open Race for Series`. It does not re-read the other Race Files during scoring, and it does not separately prove that the balanced assignment was globally optimal across every day. In balanced mode, the multi-day fairness work happens when Radio-Oracle computes the current-day preferred thirds; the quality score then verifies that the final drawn start order stayed inside those computed thirds.

## Seeded Randomization

Seeded randomization uses a stable hash function instead of platform random APIs. This is intentional: a given internal seed should produce the same start order across supported platforms and future runtime versions.

The seed only breaks flexible choices. It does not bypass rule filters. For example, a seeded draw can change which compatible category is selected first, but it still avoids same-category same-time starts when another compatible category exists.

## Quality Evaluation

`EventStartListQuality` grades the saved start times without re-running the draw. That means generated, manually edited, and imported start lists are all evaluated by the same code.

Severity meanings:

- Green: no detected rules violations or best-practice compromises.
- Orange: start-order rules are met, but the list is not ideal.
- Red: a hard rule or saved capacity setting is violated.

The score is a weighted summary from 1 to 100. It is a practical goodness factor, not a mathematical proof of optimality. Reviewers should use the score together with the emitted messages and row findings.
