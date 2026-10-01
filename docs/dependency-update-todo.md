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
- Configured: added conservative Renovate monitoring for Gradle, Kotlin/KSP,
  Compose/Skiko, AndroidX, Firebase/Google, npm, and GitHub Actions. It targets
  `Development1`, runs twice monthly, requires Dependency Dashboard approval,
  limits concurrent pull requests, and never automerges. The hosted Renovate
  GitHub App still requires explicit installation before this becomes active.
- Consider Gradle dependency locking and dependency verification metadata,
  especially because several important dependencies are resolved through
  JitPack.

## Library Robustness

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
- Fix or quarantine the stale CSV instrumentation assertions before treating
  `:app:connectedDebugAndroidTest` as a strict update gate.
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
- Keep Gradle 9.6 with AGP 9.4 for now. Gradle 9.6 is AGP 9.4's documented
  default and compiles without Gradle deprecations. Gradle 9.8 exposes
  `Configuration.setVisible` deprecations inside AGP 9.4.1, KSP 2.3.12, and
  Compose tooling, so the exact Gradle 9.8 availability notice remains as the
  sole lint-baseline entry until those upstream plugins are compatible.
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
