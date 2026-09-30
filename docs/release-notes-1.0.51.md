# Radio-Oracle 1.0.51

## Android and shared changes

- Update the Android build to Android Gradle Plugin 9 and Gradle 9.4.1 while preserving the supported Android API range.
- Fix cold-start navigation so the app reliably opens its canonical navigation host on phones and tablets.
- Add automated Android 15 testing with a 16 KB memory-page emulator and verify native-library alignment in the signed release bundle.
- Replace Crashlytics NDK with ordinary Crashlytics because Radio-Oracle has no native code of its own, reducing unnecessary native surface while retaining managed crash reporting.
- Resolve the existing Android lint and translation-error backlog, remove obsolete resources, and prevent result-service HTTP bodies from being written to diagnostic logs.

## Android release notes

Maintenance release that improves Android startup reliability and compatibility with current Android devices, including 16 KB memory-page devices. It also improves translations and reduces unnecessary diagnostic logging.

## Release validation

The local release gate passed 3,017 tests with zero failures or errors (6 skips), Android debug and instrumentation-test assembly, signed release bundling, release lint, 16 KB native compatibility, desktop runtime/distributable checks, the six-runtime jDeploy bundle check, the complete cross-platform course-transfer workflow, IOF 3.0 schema validation, jDeploy preflight/package preview, and a macOS ARM local install-and-launch smoke. npm reports zero vulnerabilities. GitHub, npm, immutable-tag workflow, artifact, and registry-smoke evidence will be recorded in `docs/release-verification-1.0.51.md` after publication.

The signed Android App Bundle is `app/build/outputs/bundle/release/app-release.aab`, version name 1.0.51 and version code 59. Its SHA-256 is `c21ef4e98262677366c7f33c3814c962bc56aeb2a094e98556a17c4fd71fe1d4`, and its upload certificate SHA-256 fingerprint is `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`. Android store submission is separate from this desktop jDeploy deployment.

Physical Android tablet and SPORTident acceptance passed on the release code before the release-only version and documentation update. Printer output and manual Windows/Linux UI acceptance are not part of this requested deployment and are not reported as passing.

The Android build temporarily retains Jetifier for SortableTableView compatibility. The waiver is limited to that legacy dependency and must be removed before Android Gradle Plugin 10. The exact 27 dependency-version advisories remain baseline-filtered for a separate dependency-modernization change rather than being mixed into this stabilization release. Android unit-test manifest merging also reports two specifically waived notices because Firebase Measurement is absent from the isolated test merge: the same removal markers are required in packaged debug and release variants to keep advertising-ID permissions out of Radio-Oracle, and the final packaged manifests are checked to confirm those permissions are absent.

The current jDeploy toolchain installs deprecated transitive development utilities while generating installers. They are not Radio-Oracle runtime code; npm audit must remain at zero vulnerabilities, and the existing patched dependency overrides remain required.

## Standards compatibility

Deployment drift review: **not-applicable**. The release candidate changes Android build configuration, resources, UI presentation, startup navigation, diagnostics, and CI compatibility checks without changing ARDF JSON, ARDF XML, IOF XML mapping, import/export formats, scoring semantics, or standards-facing race data. The current upstream `AROB-CR/radio-o-standards` HEAD remains `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1`.
