# Radio-Oracle 1.0.51 release verification

## Local candidate gates

| Gate | Result |
| --- | --- |
| Shared desktop/Android-host, desktop app, and Android debug unit suites | 3,017 tests; 0 failures; 0 errors; 6 skips |
| Android artifacts | Debug APK and instrumentation-test APK assembled; signed release AAB built; release lint found no new issues |
| Android native compatibility | Bundle requests 16 KB packaging; every packaged arm64-v8a and x86_64 ELF load segment is aligned to at least 16 KB |
| Desktop artifacts | Runtime, native distributable, and six-runtime jDeploy bundle verified |
| Course workflow | Desktop flow 16/16, Android transfer 2/2, desktop-Android-desktop round trip 3/3 |
| IOF XML | Bundled examples and focused shared, Android, and desktop schema checks passed against IOF 3.0 |
| jDeploy | Release preflight, 119.3 MB package dry run, macOS ARM install verification, and isolated launch smoke passed |
| Dependencies and static checks | npm audit: 0 vulnerabilities; actionlint, shellcheck, shfmt, diff check, function-size audit, and diff secret scan passed |
| Standards drift review | Not applicable; no standards-facing behavior changed, and upstream HEAD remains `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1` |

The preserved unfiltered test XML is under `desktopApp/build/reports/release-1.0.51/regressions/` in the release workspace.

AGP 9 exposes the application unit suite as `testDebugUnitTest`, which is the task used by both release workflows. The legacy `testReleaseUnitTest` task is not present; it is not reported as passed. Release-specific behavior is covered separately by release compilation, lint, resource shrinking, signing, and bundle verification.

## Android release bundle

- Path: `app/build/outputs/bundle/release/app-release.aab`
- Version name: 1.0.51
- Version code: 59
- SHA-256: `c21ef4e98262677366c7f33c3814c962bc56aeb2a094e98556a17c4fd71fe1d4`
- Signature: verified
- Upload certificate owner: `CN=Radio-Oracle Android Upload, OU=OpenARDF, O=OpenARDF, C=US`
- Upload certificate SHA-256: `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`
- Advertising-ID permissions: absent from the packaged release manifest

The upload certificate is intentionally self-signed. Ordinary JAR-signature verification passes; a public certificate-chain check is not applicable to this private Android upload key.

## Documented toolchain notices and waivers

- Jetifier remains enabled only for SortableTableView compatibility and must be removed before AGP 10.
- Release lint found no new issues; the exact 27 dependency-version advisories remain baseline-filtered for a separate dependency-modernization change.
- The isolated Android unit-test manifest reports two removal-marker notices because Firebase Measurement is not part of that isolated merge. The markers are retained because they remove advertising-ID permissions from packaged debug and release variants; the final release manifest confirms the permissions are absent.
- The current jDeploy toolchain installs deprecated `inflight` and `glob` development dependencies while generating installers. They are not Radio-Oracle runtime code, the patched overrides remain active, and npm audit reports zero vulnerabilities.
- JVM test workers report that class-data sharing is unavailable when their bootstrap classpath is modified. This is a test-VM runtime notice, not a source warning or test failure.

## Publication verification

The exact candidate commit, immutable tag, GitHub installer workflow, npm trusted-publish workflow and provenance, immutable-tag course workflow, public release assets and hashes, npm integrity, direct tarball availability, and fresh registry-install smoke will be added after publication.

## Physical acceptance

The release code passed physical Galaxy Tab A7 Lite startup, SPORTident connection, card read/store, disconnect, and crash-buffer acceptance before the release-only version and documentation update. No runtime code changed afterward.

## Explicitly unverified scope

Google Play submission, printer output, and manual Windows/Linux UI acceptance are not part of this requested jDeploy deployment and Android build. They are not reported as passing.
