# Coding Guidelines

## Function Size

Keep functions small enough that Kotlin/JVM bytecode size is not a design
constraint.

- Prefer functions under 100 lines.
- Treat functions over 150 lines as refactor candidates before adding more
  behavior.
- Do not add new behavior to functions near 200 lines unless the same change
  also extracts a coherent helper.
- Compose UI functions should be split by visible UI responsibility: shell,
  toolbar, dialog, section router, row, editor, and status surfaces should not
  live in one composable when they can be named separately.
- Large event/session handlers should move repeated edit, import, or export
  patterns into shared helpers instead of adding one more branch to a large
  callback.

The JVM hard limit is per generated method, not per source file. Compose
functions can generate large methods from ordinary-looking source, so repeated
UI blocks should be extracted before the compiler forces awkward design choices.

Run `just function-size` before expanding large UI or session code. The checker
reports declarations over the guideline threshold. Use
`node ./scripts/check-kotlin-function-size.mjs --strict` when a slice is expected
to stay under the threshold.

## Comments

Use comments for non-obvious invariants, platform constraints, or intentional
tradeoffs. Remove comments that only restate the code or describe behavior that
has drifted.
