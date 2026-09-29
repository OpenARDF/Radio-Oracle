# Radio-Oracle 1.0.50

## Desktop and shared changes

- Add guarded SI-Card8 owner-name inspection and programming on desktop, including exact-card and station checks, interruption recovery, removal/reinsertion verification, and punch-preservation confirmation.
- Add reviewed control-coordinate editing for unlocked courses. Changes show affected courses before application and are blocked after recorded activity could make course changes unsafe.
- Add compact PDF course reports with a course diagram, elevation profile, control labels, and ideal-route leg distances in meters.
- Resolve category assignments through the canonical race control catalog across evaluation, imports, exports, reports, and desktop course tools while retaining narrowly scoped compatibility for older Race Files and Android Room data.
- Remove obsolete condition checks and compiler deprecation warnings, and migrate desktop clipboard handling to the current Compose API.

## Android release notes

Maintenance release that improves shared course and control-data reliability and keeps Android aligned with the desktop release. There are no new Android features in this update.

## Release validation

The local release gate passed 4,101 tests with zero failures or errors (7 skips), Android debug assembly and signed release bundling, Android release lint, desktop runtime/distributable checks, the six-jar jDeploy bundle check, the complete cross-platform course-transfer workflow, IOF 3.0 schema validation, jDeploy preflight/package preview, and a macOS ARM local install-and-launch smoke. npm reports zero vulnerabilities. GitHub, npm, and immutable-tag workflow evidence will be recorded in `docs/release-verification-1.0.50.md` after publication.

The signed Android App Bundle is `app/build/outputs/bundle/release/app-release.aab`, version name 1.0.50 and version code 58. Its upload certificate SHA-256 fingerprint is `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`. Android store submission is separate from this desktop jDeploy deployment.

Physical SPORTident, printer, Android-device, and manual Windows/Linux UI acceptance are outside the requested jDeploy and Android-build scope and are not reported as passing.

The Android build retains the repository's intentional compile SDK 37 target. Android Gradle Plugin 8.13.2 emits its compatibility notice because that plugin version was tested through SDK 36.1; the warning is documented rather than suppressed, and successful debug/release compilation and signing remain required.

The latest available jDeploy release still depends on the deprecated `shelljs` 0.8 toolchain, which emits install-time notices for `glob` 7 and `inflight`. These packages are used only while generating installers, not by Radio-Oracle at runtime. The vulnerable nested `brace-expansion` version is overridden with patched 1.1.21, the bundled `tar` runtime is updated to 7.5.22, and `npm audit` must report zero vulnerabilities. Replacing jDeploy's incompatible dependency range is deferred to jDeploy upstream rather than forcing an invalid npm tree.

## Standards compatibility

Deployment drift review: **compatible**. Refreshed the current upstream `AROB-CR/radio-o-standards` schemas, examples, and README at commit `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1`. ARDF JSON and ARDF XML field shapes, namespaces, control roles, scoring semantics, and result structures remain unchanged. IOF imports continue to use schema-valid Id, Name, and PunchingUnitId data; the new preview catalog and strict control-ID resolution are internal mappings that do not add or alter IOF elements. Course coordinates and SI-Card owner data remain Radio-Oracle Race File or hardware concerns rather than additions to the public interchange formats. Existing standards differences are not expanded by this release.
