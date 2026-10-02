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

PR #16 validated the merged implementation in Windows x64 and Linux x64 installed-package workflows and the hosted course-workflow lane. The exact 1.0.53 candidate in PR #17 then passed every blocking hosted gate:

| Gate | Result |
| --- | --- |
| Android bundle and 16 KB emulator smoke | Passed in 5m38s after one infrastructure-only retry ([job](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36953797462/job/110674010327)) |
| Linux x64 installed-package smoke | Passed in 2m25s ([job](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36953797509/job/110672213865)) |
| Windows x64 installed-package smoke | Passed in 4m45s ([job](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36953797517/job/110672213873)) |
| Hosted course workflow | Passed in 7m35s ([job](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36953797505/job/110672214028)) |

The candidate's first Android emulator attempt was killed for low memory after launch. The unchanged rerun passed. After PR #17 merged, the Linux, Windows, and course-workflow runs passed again. The merge-commit Android run initially encountered an emulator package-service `Broken pipe (32)` before app installation; its unchanged retry passed the complete gate in 6m21s. These are classified as hosted-emulator readiness failures rather than application failures because both exact revisions passed without code or configuration changes. A follow-up should make the smoke runner wait for package and activity services and retry install or launch when these transient readiness signatures occur.

## Publication verification

- Merged release commit and immutable tag: `2a879ed13bc2a2ffae616c8def988c5e8a4eb146`, `v1.0.53`
- GitHub release workflow: [passed](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36954964392) in 12m10s from the tagged commit
- Public GitHub release: [Radio-Oracle 1.0.53](https://github.com/OpenARDF/Radio-Oracle/releases/tag/v1.0.53), published, non-draft, and non-prerelease with 14 assets
- npm trusted-publish workflow: [passed](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36955987977) in 11m18s from the tagged commit; Sigstore transparency-log index `3046334589`
- Public npm package: `@openardf/radio-oracle@1.0.53`; SHA-1 `9a0e7bdca4b7d6f9464cbc2148b669b32e3aaa93`; integrity `sha512-b3lg0QVeWLB9EquUysbuUoo7DumdvcSJG3UybXrnQmLAaHrGcrxoFpj8zMh/cTiAsauH4NS6LlHH/EX0LLfFTA==`; `gitHead` matches the tagged commit
- Fresh registry-install smoke: passed from an isolated temporary directory and launched the installed application
- GitHub-safe source package: `radio-oracle-1.0.53.tgz`, 118,526,022 bytes, SHA-256 `4ac24e7cf7030d9e7f411e33aeeceed17df0af3f4d841afb4c6594ba33d12236`

All 14 GitHub assets were downloaded independently after publication. Their computed SHA-256 hashes matched GitHub's recorded digests. Inspection of the downloaded package confirmed version 1.0.53, jDeploy 6.1.7, Java 17, tag `v1.0.53`, and commit `2a879ed13bc2a2ffae616c8def988c5e8a4eb146`. The public install link remains `https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle`.

## Google Play

Google Play Console accepted version code 61 / version name 1.0.53 into the OpenARDF Internal testing track on October 2, 2026 at 7:43 AM EDT. The release is active and shown as **Available to internal testers** with the Android release notes from `docs/release-notes-1.0.53.md`.

Play reports API 26+, target SDK 37, all four packaged ABIs, explicit 16 KB memory-page support, and 17,790 supported devices. Compared with 1.0.52, the release removes support for zero devices and adds zero devices: 11,299 phones, 6,395 tablets, 5 TVs, 10 cars, 77 Chromebooks, and 1 Android XR device remain supported.

Play's only release warning says that the bundle contains native code without uploaded native debug symbols. Radio-Oracle has no native code of its own; the packaged ELF files come from third-party AndroidX DataStore dependencies, are stripped, and passed the repository's 16 KB compatibility checks. The warning is therefore specifically waived for this release: uploading symbols would improve third-party native crash detail but does not affect installation, execution, or compatibility.

## Explicitly unverified scope

Printer output and manual Windows/Linux UI acceptance are not part of this release. Automated installed-package acceptance covers the supported Windows x64 and Linux x64 release artifacts; no manual UI result is inferred from it.
