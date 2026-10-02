# Repository Instructions

For every code-modifying task in this repository:

1. Load and follow the `openardf-workflow` skill before editing.
2. Do not report completion until its required implementation preflight and blocking completion gate pass.
3. Fix workflow violations found in the final diff audit unless blocked; do not merely list avoidable violations.
4. Include OpenARDF compliance evidence in the final response for code reuse, shared versus platform-specific placement, source comments, and tests or checks.
5. Begin development work from a clean, synchronized `Development1` checkout on a focused `codex/<feature>` branch. Reuse a suitable existing topic branch when one already contains the intended work. Do not implement directly on `Development1` unless the user explicitly requests that exception; merge validated topic branches through a reviewed pull request.

When asked to respond to an email for Charles Scharlau, sign it `Codex for Charles Scharlau`.
