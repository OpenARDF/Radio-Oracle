# Radio-Oracle 1.0.52 release verification

## Local candidate gates

| Gate | Result |
| --- | --- |
| Shared desktop/Android-host, desktop app, and Android debug unit suites | 3,023 tests; 0 failures; 0 errors; 6 skips |
| Android physical instrumentation | Complete suite passed on a Moto g 5G (2024), Android 15, over wireless ADB |
| Android artifacts | Debug APK assembled; signed release AAB built; release lint found no new issues |
| Android native compatibility | Bundle requests 16 KB packaging; every packaged arm64-v8a and x86_64 ELF load segment is aligned to at least 16 KB |
| Desktop artifacts | Runtime, native macOS distributable, and six-runtime jDeploy bundle verified |
| Course workflow | Desktop flow 16/16, Android transfer 2/2, desktop-Android-desktop round trip 3/3 |
| IOF XML | Bundled examples and focused shared, Android, and desktop schema checks passed against IOF 3.0 |
| jDeploy | Release preflight, 118.5 MB package dry run, macOS ARM native install verification, and isolated launch smoke passed |
| Dependencies and static checks | npm audit: 0 vulnerabilities; actionlint, shellcheck, shfmt, diff check, function-size audit, and full-history redacted secret scan passed |
| Standards drift review | Not applicable; no standards-facing behavior changed, and authoritative upstream HEAD remains `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1` |

The preserved unfiltered test XML is under `desktopApp/build/reports/release-1.0.52/regressions/` in the release workspace. Suite counts are: shared desktop 762, shared Android host 762, desktop app 1,172 with 5 skips, and Android app 327 with 1 skip.

Direct Gradle task discovery was also checked explicitly. `./gradlew :desktopApp:tasks --warning-mode=all` passed without requiring any SPORTident hardware property; the required values are now resolved only when their corresponding device task executes.

## Android release bundle

- Path: `app/build/outputs/bundle/release/app-release.aab`
- Version name: 1.0.52
- Version code: 60
- SHA-256: `9d5d42fcf3dadb037188e103aefe4da954cab309c6d840f595fe60c33942887f`
- Signature: verified
- Upload certificate owner: `CN=Radio-Oracle Android Upload, OU=OpenARDF, O=OpenARDF, C=US`
- Upload certificate SHA-256: `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`
- Advertising-ID permissions: absent from the packaged release manifest

The upload certificate is intentionally self-signed. Ordinary JAR-signature verification passes; a public certificate-chain check and timestamp are not applicable to this private Android upload key. The JAR tool's stream-layout and POSIX-attribute notices describe the Android App Bundle container rather than a signature failure.

## Documented toolchain notices and waivers

- Release lint found no new issues. Its one baseline-filtered item is the reviewed Gradle-version notice: Android Gradle Plugin 9.4 defaults to Gradle 9.6, while newer Gradle versions currently expose upstream AGP, KSP, and Compose deprecations.
- The isolated Android unit-test manifest reports two removal-marker notices because Firebase Measurement is absent from that test merge. The final packaged release manifest confirms that `com.google.android.gms.permission.AD_ID` and `android.permission.ACCESS_ADSERVICES_AD_ID` are absent.
- Mockito test workers report that class-data sharing is unavailable when Mockito modifies their bootstrap classpath. This is a test-VM startup-optimization notice, not a source warning or test failure.
- The Android upload certificate is intentionally self-signed and untimestamped. Google Play verifies the upload key and applies distribution signing separately.

## Physical acceptance

The complete instrumentation suite passed on the wirelessly connected Moto g 5G (2024), Android 15. Radio-Oracle then received USB permission for SI MASTER station 554900, reported readout readiness, read card 2005010 with 9 punches, and logged `SI Card read stored`. The card's deliberately unrelated test-race timing produced the expected non-blocking `FINISH_BEFORE_CONTROL` result. After the reader was unplugged, Radio-Oracle returned to `DISCONNECTED` with no Android runtime crash.

## Hosted installed-package acceptance

- [Windows x64 installed-package workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36903711575): passed in 3m51s, including installation, export, and launch.
- [Linux x64 installed-package workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36903711565): passed in 3m39s, including installation, export, and launch under Xvfb.
- [Android native-compatibility workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36903712063): passed in 7m21s, including Firebase-enabled release inspection and Android 15 16 KB emulator launch.
- [Course workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36903711536): passed in 8m38s, including lifecycle reports and desktop-Android-desktop archive transfer.

## Publication verification

The exact candidate commit, immutable tag, GitHub installer workflow, npm trusted-publish workflow and provenance, immutable-tag course workflow, public release assets and hashes, npm integrity, direct tarball availability, and fresh registry-install smoke will be added after publication.

## Google Play

Google Play submission remains pending. It is not reported as complete until Play Console accepts the signed version-code 60 bundle into the intended release track.

## Explicitly unverified scope

Printer output and manual Windows/Linux UI acceptance are not part of this maintenance release. Automated installed-package acceptance covers the supported Windows x64 and Linux x64 release artifacts; no manual UI result is inferred from it.
