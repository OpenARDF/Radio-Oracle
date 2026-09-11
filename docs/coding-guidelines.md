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

## Scrolling and dialogs

- Use `DesktopAlertDialog` for desktop alerts. Its body scrolls inside a bounded
  viewport while the title and actions remain visible.
- Use `DesktopWorkspaceScroll` for desktop workspaces and custom dialog bodies.
  Put variable-length filenames, warnings, and options inside the viewport.
  Reserve space for fixed actions before measuring the body; avoid nested
  vertical scroll areas when a panel already lives inside the workspace.
- Custom Android dialog layouts must provide a bounded `ScrollView`. Keep
  primary actions outside it when practical. A readout's bounded punch list can
  participate in the form's scrolling; retain native recycling and scrolling
  for unbounded race and competitor lists.
- Extend the scrolling regression tests when adding a custom dialog. Verify
  the final option at small window sizes and enlarged text, rather than just
  checking that a scrollbar exists. See [the scrolling audit](ui-scrolling-audit.md).

## Comments

Use comments for non-obvious invariants, platform constraints, or intentional
tradeoffs. Remove comments that only restate the code or describe behavior that
has drifted.
