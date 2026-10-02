# Radio-Oracle 1.0.53 release verification

## Local candidate gates

| Gate | Result |
| --- | --- |
| Shared desktop/Android-host, desktop app, and Android debug unit suites | 3,035 tests; 0 failures; 0 errors; 6 skips |
| Android physical instrumentation | 12/12 tests passed on a Moto g 5G (2024), Android 15, over wireless ADB |
| Android artifacts | Debug APK assembled; signed release AAB built; release lint found no new issues |
| Android native compatibility | Bundle requests 16 KB packaging; every packaged arm64-v8a and x86_64 ELF load segment is aligned to at least 16 KB |
| Desktop artifacts | Runtime, native macOS distributable, and six-runtime jDeploy bundle verified |
| Course workflow | Desktop flow 16/16, Android transfer 2/2, desktop-Android-desktop round trip 3/3 |
| IOF XML | Bundled examples and focused shared, Android, and desktop schema checks passed against IOF 3.0 |
| jDeploy | Release preflight, 118.5 MB package dry run, macOS ARM native install verification, and isolated launch smoke passed |
| Dependencies and static checks | npm audit: 0 vulnerabilities; actionlint, shellcheck, shfmt, diff check, function-size audit, and 1,521-commit redacted secret scan passed |
| Standards drift review | Not applicable; no standards-facing behavior changed, and authoritative upstream HEAD remains `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1` |

The preserved unfiltered test XML is under `desktopApp/build/reports/release-1.0.53/regressions/` in the release workspace. Suite counts are: shared desktop 762, shared Android host 762, desktop app 1,172 with 5 skips, and Android app 339 with 1 skip. The 12-test Moto instrumentation XML is preserved beside it under `android-instrumentation/`.

Direct Gradle task discovery was checked explicitly. `./gradlew :desktopApp:tasks --warning-mode=all` passed without requiring any SPORTident hardware property.

## Android release bundle

- Version name: 1.0.53
- Version code: 61
- Path: `app/build/outputs/bundle/release/app-release.aab`
- SHA-256: `c9a54262a097af994c5e70e842b3990f00439c977a8ca1381f0df4ea03bb8f65`
- Signature: verified
- Upload certificate owner: `CN=Radio-Oracle Android Upload, OU=OpenARDF, O=OpenARDF, C=US`
- Upload certificate SHA-256: `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`
- Advertising-ID permissions: absent from the packaged release manifest

The packaged manifest identifies `org.openardf.radiooracle`, version name 1.0.53, and version code 61. The upload certificate is intentionally self-signed. Ordinary JAR-signature verification passes; a public certificate-chain check and timestamp are not applicable to this private Android upload key. The JAR tool's stream-layout and POSIX-attribute notices describe the Android App Bundle container rather than a signature failure.

## Documented toolchain notices and waivers

- Release lint's reviewed Gradle-version notice remains categorically waived while Android Gradle Plugin 9.4 defaults to Gradle 9.6 and newer Gradle versions expose upstream AGP, KSP, and Compose deprecations.
- The isolated Android unit-test manifest's two Firebase permission-removal notices remain specifically waived because Firebase Measurement is absent from that test merge; the packaged release manifest is the authoritative permission check.
- Mockito test workers' class-data-sharing notice remains categorically waived because it describes test-VM startup optimization after Mockito modifies the bootstrap classpath.
- The Android upload certificate is intentionally self-signed and untimestamped; Google Play verifies the upload key and applies distribution signing separately.

## Physical acceptance

The exact 1.0.53 candidate passed all 12 instrumentation tests on the wirelessly connected Moto g 5G (2024), Android 15.

Before the version-only release transition, the same merged application code completed a bounded persistent-write acceptance on SI MASTER station 554900 and SI-Card8 2450663: it read `Elmer Fudd`, wrote `Elmo Fudder`, automatically read the result back, preserved the existing punch, changed no unrelated card bytes, and cleared the recovery record. The owner-name fields and Write Names action remained available after card removal, and maintenance reads did not open the Duplicate Card dialog. Because the only subsequent candidate changes are numeric version metadata and documentation, that destructive hardware write was not repeated.

## Hosted installed-package acceptance

PR #16 validated the merged implementation in Windows x64 and Linux x64 installed-package workflows and the hosted course-workflow lane. Exact 1.0.53 candidate workflows remain pending before publication.

## Publication verification

The exact candidate commit, immutable tag, GitHub installer workflow, npm trusted-publish workflow and provenance, immutable-tag course workflow, public release assets and hashes, npm integrity, direct tarball availability, and fresh registry-install smoke will be added after publication.

## Google Play

Google Play submission remains pending. It is not reported as complete until Play Console accepts the signed version-code 61 bundle into the Internal testing track with the Android release notes from `docs/release-notes-1.0.53.md`.

## Explicitly unverified scope

Printer output and manual Windows/Linux UI acceptance are not part of this release. Automated installed-package acceptance covers the supported Windows x64 and Linux x64 release artifacts; no manual UI result is inferred from it.
