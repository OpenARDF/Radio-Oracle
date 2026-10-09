# Dependency Update TODO

This list was intentionally deferred until after Android regression testing,
baseline labeling, and SI reader hardware checks. Those Android hardware checks
passed on 2026-05-31 and are recorded in
[`android-regression-2026-05-31.md`](android-regression-2026-05-31.md). The
current multiplatform foundation work should remain behavior-preserving for
Android while giving future desktop work a cleaner shared-code base.

## Dependency Management

- Every two weeks, or weekly during an active release cycle, review authoritative
  release notes and version sources for jDeploy and the libraries, build tools,
  runtimes, SDKs, and GitHub Actions used by Radio-Oracle. Record useful,
  security-relevant, or compatibility-relevant candidates here before changing
  versions; an available update is not by itself approval to adopt it.
- Done: introduced a Gradle version catalog at `gradle/libs.versions.toml`
  without changing dependency versions.
- Done: moved root plugin versions and app library coordinates into the version
  catalog so future updates are easier to review.
- Keep coupled versions grouped together, especially Kotlin/KSP, Room
  runtime/compiler, Navigation plugin/runtime artifacts, OkHttp artifacts, and
  AndroidX test artifacts.
- Active: added conservative Renovate monitoring for Gradle, Kotlin/KSP,
  Compose/Skiko, AndroidX, Firebase/Google, npm, and GitHub
  Actions. It targets `Development1`, runs twice monthly, requires Dependency
  Dashboard approval, limits concurrent pull requests, and never automerges.
  The hosted Renovate GitHub App was installed on 2026-10-01 with access limited
  to `OpenARDF/Radio-Oracle`; Mend silent mode is disabled, and automated pull
  requests, required repository configuration, and onboarding are enabled.
  Mend's repository base branch is explicitly `Development1` because GitHub's
  default branch remains `main`. Onboarding PR #10 was reviewed with the
  repository's conservative configuration and merged to `main`, where Renovate
  requires its effective repository configuration. Mend subsequently reports
  Renovate as Interactive, and the GitHub App has read/write access to issues,
  code, checks, commit statuses, pull requests, and workflows. Dependency
  Dashboard issue #12 appeared on 2026-10-01. Its first single-update approval
  created PR #14 for Android Emulator Runner 2.38.0; the 16 KB emulator and
  course-workflow gates passed before it merged. All other dashboard candidates
  remain approval-gated. The onboarding scan's sole dependency-lookup warning
  was a transient Maven Central rate limit; direct metadata confirmed
  Expandable FAB 1.2.1 is already current.
- Consider Gradle dependency locking and dependency verification metadata,
  especially because several important dependencies are resolved through
  JitPack.

## Library Robustness

- Done: removed the unused, relocated `kotlin-csv-jvm` dependency rather than
  migrating an unnecessary runtime library. Android CSV code already delegates
  to the shared `CsvCodec` and `EventCsv*` import, row, and export abstractions;
  their existing round-trip suites remain the compatibility gate.
- Done: moved `androidx.test.ext:junit-ktx` out of app runtime dependencies
  and into the instrumentation-test dependency scope.
- Done: isolated SortableTableView 2.8.1 as a pinned, repository-owned AndroidX
  compatibility module. Its package and public API remain unchanged while its
  legacy Support Library references use AndroidX directly. The published JitPack
  dependency, obsolete support transitives, and `android.enableJetifier` are no
  longer part of the application build.
- Done: removed production `HttpLoggingInterceptor.Level.BODY` logging and the
  unused logging-interceptor dependency, so live result payloads are no longer
  copied into verbose network logs.
- Done: removed the unrelated `app/libs/android-tableview-kotlin-0.1.0-alpha`
  source tree. The current compatibility module is instead pinned directly to
  the upstream Java 2.8.1 release and records its provenance and AndroidX-only
  changes alongside the source.
- Keep shared-module dependencies minimal so the desktop target remains easy to
  build and library updates do not drag Android-only APIs into shared code.

## Verification Policy

- Use the existing project gate for normal dependency-management changes:
  `./gradlew :shared:check :app:testDebugUnitTest :shared:desktopSmokeRun :app:assembleDebug :app:assembleDebugAndroidTest`.
- For dependency version updates, also run Android hardware checks when
  practical, including install, launch, foreground/logcat smoke, and SI reader
  connect/read/disconnect once hardware is available.
- Done: made `just android-connected-test` a strict device gate. The active CSV
  assertions already matched the shared export contract, so their pure Android
  entity adapters now run in the JVM suite and the obsolete no-op CSV device
  test was removed. The baseline run instead exposed two stale control-parser
  fixtures; those now follow the shared semicolon-delimiter and sprint-duplicate
  rules. The complete eight-test instrumentation suite then passed on a Moto g
  5G (2024) running Android 15.
- JVM unit-test workers can report that class-data sharing is unavailable when
  Mockito modifies the bootstrap classpath. This waiver applies only to that
  VM runtime notice when the affected test task completes successfully.
- Done: updated the release-lint version notices in compatibility groups after
  Jetifier removal: AGP and Gradle; coupled AndroidX families; Firebase and
  Google tooling; then independent runtime and test libraries. Android and
  desktop gates passed after each applicable group.
- Done: kept the jDeploy Skiko native runtimes coupled to Compose Multiplatform;
  Compose 1.12.1 requires Skiko 0.150.1.
- Done: updated the exact jDeploy development-tool pin from 6.1.3 to 6.1.7 in
  a separate maintenance change. The local smoke now requests `install
  --native` explicitly to preserve native-app installation after jDeploy 6.1.5
  changed plain `install` to npm-link mode. The update does not remove
  jDeploy's deprecated `shelljs` 0.8, `glob` 7, or `inflight` transitives.
- Keep the bundled launcher on exact `shelljs` 0.8.5 while the current 0.10
  line resolves through vulnerable `braces` 3.0.3 and no patched `braces`
  release exists. jDeploy already requires the compatible 0.8 line, and the
  existing `brace-expansion` override keeps that line's glob dependency
  patched. Re-evaluate the pin when ShellJS or `braces` publishes a safe
  replacement; `npm audit` must remain at zero vulnerabilities.
- Security exception: update Gradle 9.6.0 to 9.8.1 even though AGP 9.4 documents
  9.6 as its default. Gradle 9.6.0 is affected by
  [GHSA-mvvg-497x-hmj8](https://github.com/gradle/gradle/security/advisories/GHSA-mvvg-497x-hmj8),
  [GHSA-xwqc-3h47-hg64](https://github.com/gradle/gradle/security/advisories/GHSA-xwqc-3h47-hg64),
  and [GHSA-j5m7-59rp-24f5](https://github.com/gradle/gradle/security/advisories/GHSA-j5m7-59rp-24f5).
  Gradle 9.8.1 is the publicly available patched 9.x release; the 9.6.2
  backport requires a Gradle Security Subscription.
- Temporarily waive only the two upstream configuration warnings reproduced by
  `just gradle --no-daemon --no-configuration-cache --warning-mode all help`:
  `Configuration.setVisible(boolean)` while configuring `:app`, and Compose
  Hot Reload's unsupported `HotReloadUsageType` attribute while configuring
  `:desktopApp`. No
  Radio-Oracle source uses either API. Remove this waiver as soon as compatible
  AGP, KSP, and Compose tooling is available; the Hot Reload warning becomes an
  error in Gradle 10. Any additional Gradle warning remains blocking.
- Record dependency-update evidence in commit messages or release notes so the
  source development team can see what was changed, what was verified, and what
  remains deferred.

## Handoff Goal

- Preserve a clean, labeled Android-equivalent baseline that the upstream/fork
  source development team can inspect without needing to accept future desktop
  work.
- Keep handoff branches and tags understandable without Codex-specific context.
- Prefer small, reviewable dependency-update commits after the handoff baseline,
  so the source team can adopt the refactored shared-code foundation without
  also taking unrelated library churn.
