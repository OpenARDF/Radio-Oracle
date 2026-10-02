# Radio-Oracle 1.0.53

## SPORTident tools

- Add a dedicated Android SPORTident Tools hub with separate Station Maintenance, SI Card Tools, and Punch History screens.
- Add Android station inspection, clock synchronization, and station sleep controls equivalent to the desktop tools.
- Add continuous SI-Card8 reading, punch-history inspection, and owner-name editing through a guarded write-and-readback transaction.
- Retain the card's editable name after removal, keep the write action available until reinsertion, and make insertion/removal instructions prominent.
- Keep maintenance card reads isolated from race processing so they do not trigger duplicate-card dialogs.
- Preserve punches and unrelated card data during owner-name writes, with crash-recovery records and independent verification.

## Android release notes

Adds dedicated SPORTident tools for station maintenance, punch-history inspection, and automatic SI-card reading. SI-Card8 owner names can now be edited through a guarded write-and-readback workflow that preserves punch data and verifies the card before and after writing.

## Release validation

The local release gate passed 3,035 tests with zero failures or errors (6 skips), the complete 12-test Moto Android 15 instrumentation suite, Android debug assembly, signed release bundling, release lint, 16 KB native compatibility, desktop runtime/distributable checks, the six-runtime jDeploy bundle check, the complete cross-platform course-transfer workflow, IOF 3.0 schema validation, jDeploy preflight/package preview, and a macOS ARM native install-and-launch smoke. npm reports zero vulnerabilities.

The signed Android App Bundle is `app/build/outputs/bundle/release/app-release.aab`, version name 1.0.53 and version code 61. Its SHA-256 is `c9a54262a097af994c5e70e842b3990f00439c977a8ca1381f0df4ea03bb8f65`, and its upload certificate SHA-256 fingerprint is `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`.

The exact 1.0.53 candidate passed Moto instrumentation. The merged implementation also passed a real station 554900 / SI-Card8 2450663 write-and-readback acceptance, preserving its punch and all unrelated card bytes while changing the requested owner name and clearing recovery state. The only changes after that hardware acceptance are release version metadata and documentation, so the destructive card write was not repeated. Exact-candidate hosted workflows, immutable-tag workflows, publication evidence, and Google Play submission will be recorded in `docs/release-verification-1.0.53.md` as they complete.

The isolated Android unit-test manifest may report two specifically waived Firebase permission-removal notices because Firebase Measurement is absent from that test merge. The final packaged manifest is checked instead and must exclude both advertising-ID permissions. Mockito test workers may also report that class-data sharing is unavailable after Mockito appends its bootstrap classpath; this affects only test-VM startup optimization. Release lint retains one reviewed Gradle-version notice because Android Gradle Plugin 9.4 defaults to Gradle 9.6 and newer Gradle versions currently expose upstream deprecations. These notices are not Radio-Oracle source warnings.

## Standards compatibility

Deployment drift review: **not-applicable**. This release adds SPORTident maintenance UI and device operations without changing ARDF JSON, ARDF XML, IOF XML mapping, import/export formats, scoring semantics, or standards-facing race data. The authoritative `AROB-CR/radio-o-standards` HEAD remains `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1`.
