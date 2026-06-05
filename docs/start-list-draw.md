# Start List Draw Algorithm

This document describes the shared start-list generator used by the desktop Start List screen. The implementation lives in `shared/src/commonMain/kotlin/org/openardf/radiooracle/shared/event/EventProjectEditor.kt`; persisted settings are modeled in `StartDrawOptions.kt`; grading and color-coded findings are modeled in `EventStartListDetails.kt`.

## Goals

The generator assigns relative start times to competitors while balancing hard limits and best-practice spacing:

- Do not exceed the configured number of starters per start time.
- Avoid multiple competitors from the same category at the same start time.
- Avoid consecutive competitors from the same category when another category is available.
- Avoid same-club starts when `Avoid same club` is enabled.
- Avoid same first-fox conflicts for similarly fast categories when protected course-order data is available.
- Make non-default seeded draws repeatable across machines and runs.

Some events do not have enough categories or clubs to satisfy every best practice. In that case the generator completes the draw, and the evaluator reports the remaining compromises as orange or red findings.

## Persisted Settings

Start List settings are stored in the event project data, not desktop-local preferences. The persisted values are:

- `intervalSeconds`
- `clubHandling`
- `startersPerStartTime`
- `seed`

The default seed is `default`. That value is visible and persisted, but it preserves deterministic category/start-number ordering. Any non-default seed activates repeatable pseudo-random tie-breaking. Blank seeds are normalized back to `default` before settings are saved or a draw is run.

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
3. Prefer a category different from the category that ended the previous start time.
4. If club avoidance is enabled, reject queues whose head competitor would duplicate a club already selected for this same start time.
5. If club avoidance is enabled, prefer a queue whose head competitor differs from the club that ended the previous start time.
6. Apply deterministic or seeded tie-breakers to the remaining queues.

Same-category adjacency and same-club adjacency across start times are best-practice filters. If every remaining queue violates one of those preferences, the algorithm falls back and continues the draw. Same-club duplication inside the same start time is stricter: the algorithm leaves the start time partially filled rather than knowingly adding a duplicate-club starter to that same slot.

## Competitor Selection Within a Queue

Most selection happens at category level, but club conflicts are competitor-specific. After a category queue is selected, the algorithm may look past the first competitor in that category queue to find a later competitor whose club avoids the current slot and previous-slot club conflicts. This preserves the category choice while improving club spacing.

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
