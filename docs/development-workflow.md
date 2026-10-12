# Development And Release Branch Workflow

`Development1` is Radio-Oracle's integration branch. `main` remains the default
branch and the release branch.

## Ordinary Development

Start each ordinary development slice from a clean, synchronized
`Development1` checkout. Create a focused `codex/<feature>` topic branch from
that exact revision; do not implement ordinary changes directly on
`Development1`.

Return the topic branch through a pull request into `Development1`. The
always-running `course-workflow` GitHub Actions check is the required
build/test gate. The check is intentionally loose: an otherwise clean topic
branch does not have to be updated to the latest `Development1` revision before
merging. Resolve every review conversation before merge. Formal approval is
optional so that the single maintainer is not deadlocked waiting for another
approver.

Use squash or rebase merging to preserve linear history. GitHub automatically
deletes a merged remote topic branch; remove the matching local branch after
verifying that its tree matches the merged result. Both protected branches
require linear history and reject force pushes and branch deletion, including
for administrators.

## Full Releases

Prepare each release candidate on a `codex/release-X.Y.Z` topic branch created
from clean, synchronized `Development1`. Merge the candidate into
`Development1` through the same protected pull-request workflow after all
applicable release gates and the required hosted check pass.

The loose branch-wide status policy does not relax release validation.
Immediately before merging a release candidate, verify that it is current with
`Development1`. If `Development1` advanced after any release gate ran, update
the candidate to the final base and rerun every applicable local and hosted
release gate before merging.

The normal release transition remains a fast-forward push of the validated
release commit from `Development1` to `main`; `main` does not require a pull
request or status check. Verify that `main` is an ancestor of the release
commit before attempting that push. Do not add a merge commit, force-push, or
weaken `main` protection to manufacture a release transition. Tag and publish
the exact commit required by the release checklist.

`Development1` remains the authoritative source for release history. Keep its
pull-request, status-check, linear-history, force-push, and deletion protections
enabled throughout the release. Release code and post-publication evidence must
reach `Development1` through protected pull requests; do not commit release
evidence directly on `main`.

1. Freeze ordinary merges to `Development1` before fast-forwarding `main`, and
   ensure that no pending pull request has an armed auto-merge request that can
   land during the release. Keep later development queued until synchronization
   finishes.
2. Fast-forward `main` from the protected release candidate on `Development1`,
   tag that exact candidate, and complete publication and immutable-artifact
   verification.
3. Create a post-release evidence topic branch from the tagged `Development1`
   commit. Add only release-verification records and related documentation, then
   return it through the normal pull-request and `course-workflow` gate.
4. After the evidence pull request is merged, fast-forward `main` from
   `Development1` and push it without moving the release tag.
5. Fetch both remote refs and verify that `origin/main` and
   `origin/Development1` resolve to the same final evidence commit before
   resuming queued merges.

If the branches are not in a fast-forward relationship, stop and reconcile the
unexpected divergence explicitly before release. Do not force-push, merge
unvalidated development into the release branch, bypass a failing gate, or use
direct `Development1` pushes for release evidence or ordinary work.
