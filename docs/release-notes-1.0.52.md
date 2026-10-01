# Radio-Oracle 1.0.52

## Maintenance and platform changes

- Remove Jetifier and replace the obsolete SortableTableView binary with an isolated, repository-owned AndroidX-compatible module based on the same upstream implementation.
- Update reviewed Android, Kotlin, Compose, Gradle, GitHub Actions, and desktop dependencies while retaining the supported Java 17 and Android API ranges.
- Update jDeploy to 6.1.7, install native desktop packages during local acceptance, preserve colliding runtime artifacts safely, and use a Linux-compatible `.roseries` document MIME association.
- Add Windows x64 and Linux x64 installed-package smoke workflows, strengthen the Android 16 KB emulator lane, and add review-gated Renovate dependency proposals.
- Make the Android CSV device tests deterministic, move platform-neutral CSV entity checks to the JVM suite, and remove the unused `kotlin-csv` dependency.
- Keep Firebase advertising-ID removal markers out of Android test-only manifest merges while retaining them in Firebase-enabled packaged variants.
- Defer SPORTident Gradle task parameters until their hardware task actually executes. Direct task discovery such as `./gradlew :desktopApp:tasks` now works without the unrelated `siOwnerStation` property.

## Android release notes

Maintenance release improving compatibility, packaging, automated testing, and dependency health. It removes obsolete Android compatibility infrastructure and strengthens physical-device and 16 KB-page testing without changing race-day workflows or supported data formats.

## Release validation

The local release gate passed 3,023 tests with zero failures or errors (6 skips), the complete Moto Android 15 instrumentation suite, Android debug assembly, signed release bundling, release lint, 16 KB native compatibility, desktop runtime/distributable checks, the six-runtime jDeploy bundle check, the complete cross-platform course-transfer workflow, IOF 3.0 schema validation, jDeploy preflight/package preview, and a macOS ARM native install-and-launch smoke. npm reports zero vulnerabilities.

The signed Android App Bundle is `app/build/outputs/bundle/release/app-release.aab`, version name 1.0.52 and version code 60. Its SHA-256 is `9d5d42fcf3dadb037188e103aefe4da954cab309c6d840f595fe60c33942887f`, and its upload certificate SHA-256 fingerprint is `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`.

Physical Moto instrumentation and SPORTident card-read acceptance passed on the release candidate. Windows and Linux installed-package acceptance, the Android 15 16 KB emulator lane, and the hosted course workflow also passed. Immutable-tag workflows, publication evidence, and Google Play submission will be recorded in `docs/release-verification-1.0.52.md` as they complete.

The isolated Android unit-test manifest reports two specifically waived Firebase permission-removal notices because Firebase Measurement is absent from that test merge. The final packaged manifest is checked instead and excludes both advertising-ID permissions. Mockito test workers also report that class-data sharing is unavailable after Mockito appends its bootstrap classpath; this affects only test-VM startup optimization. Release lint retains one reviewed Gradle-version notice because Android Gradle Plugin 9.4 defaults to Gradle 9.6 and newer Gradle versions currently expose upstream deprecations. These notices are not Radio-Oracle source warnings.

## Standards compatibility

Deployment drift review: **not-applicable**. This release changes dependencies, platform integration, packaging, and verification infrastructure without changing ARDF JSON, ARDF XML, IOF XML mapping, import/export formats, scoring semantics, or standards-facing race data. The authoritative `AROB-CR/radio-o-standards` HEAD remains `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1`.
