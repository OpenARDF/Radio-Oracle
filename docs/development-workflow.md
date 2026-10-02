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

The normal release transition remains a fast-forward push of the validated
release commit from `Development1` to `main`; `main` does not require a pull
request or status check. Verify that `main` is an ancestor of the release
commit before attempting that push. Do not add a merge commit, force-push, or
weaken `main` protection to manufacture a release transition. Tag and publish
the exact commit required by the release checklist.

Radio-Oracle release completion requires `main` and `Development1` to contain
the exact same tag and release-evidence commits. This is the only controlled
exception that permits a direct push to `Development1`:

1. Finish the normal fast-forward release and evidence commits on `main`.
2. Keep linear-history, force-push, and deletion protections enabled on
   `Development1` throughout the synchronization.
3. Temporarily suspend only `Development1`'s required-pull-request and
   required-status-check settings.
4. Fast-forward `Development1` to the exact `main` commit and push it without a
   merge commit.
5. Immediately restore the required-pull-request rule and the app-pinned
   `course-workflow` status check, then read back the protection to verify both
   settings and the unchanged safety rules.

If the branches are not in a fast-forward relationship, stop and reconcile the
unexpected divergence explicitly before release. Do not treat this exception
as permission to force-push, merge divergent protected branches, bypass a
failing gate, or use direct `Development1` pushes for ordinary work.
