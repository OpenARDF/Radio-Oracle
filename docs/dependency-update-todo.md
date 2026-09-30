# Dependency Update TODO

This list was intentionally deferred until after Android regression testing,
baseline labeling, and SI reader hardware checks. Those Android hardware checks
passed on 2026-05-31 and are recorded in
[`android-regression-2026-05-31.md`](android-regression-2026-05-31.md). The
current multiplatform foundation work should remain behavior-preserving for
Android while giving future desktop work a cleaner shared-code base.

## Dependency Management

- Done: introduced a Gradle version catalog at `gradle/libs.versions.toml`
  without changing dependency versions.
- Done: moved root plugin versions and app library coordinates into the version
  catalog so future updates are easier to review.
- Keep coupled versions grouped together, especially Kotlin/KSP, Room
  runtime/compiler, Navigation plugin/runtime artifacts, OkHttp artifacts, and
  AndroidX test artifacts.
- Add dependency update automation, preferably Renovate, with conservative
  grouping rules for Gradle, Kotlin, AndroidX, Firebase, and JitPack libraries.
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
- The Jetifier-removal change deliberately retains Android Gradle Plugin 9.2.1,
  AndroidX Core 1.18.0, and SwipeRefreshLayout 1.0.0. Release lint reports newer
  versions, but upgrading them is deferred so this compatibility-only change
  does not combine toolchain or runtime-behavior changes with dependency
  isolation. Revisit each version in a separate dependency-maintenance change.
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
