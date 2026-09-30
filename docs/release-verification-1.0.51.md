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
- Android debug and unit-test manifest merging repeats two removal-marker notices because Firebase Measurement is not present in those particular merge inputs. The markers are retained because removing them causes advertising-ID permissions to reappear in packaged variants; the final signed release manifest confirms both permissions are absent.
- The current jDeploy toolchain installs deprecated `inflight` and `glob` development dependencies while generating installers. GitHub-hosted Node actions and npm/jDeploy tooling also report Node's deprecated `punycode` module and legacy `url.parse()` API. These are build-tool processes rather than Radio-Oracle runtime code, the patched dependency overrides remain active, and npm audit reports zero vulnerabilities.
- JVM test workers report that class-data sharing is unavailable when their bootstrap classpath is modified. This is a test-VM runtime notice, not a source warning or test failure.
- The macOS hosted runner used software OpenGL for the headless desktop smoke. The Android emulator also logged one protected-range unmap warning while initializing virtual memory, then booted successfully and passed the Android 15 16 KB app-start smoke. Both are runner/emulator diagnostics rather than warnings produced by Radio-Oracle source.
- GitHub reports that `actions/setup-node@v4`, `actions/upload-artifact@v4`, `android-actions/setup-android@v3`, and `xresloader/upload-to-github-release@v1.6.0` still target Node 20 and are being forced onto Node 24. It also announced the future `ubuntu-latest` migration to Ubuntu 26 and possible macOS ARM queue delays. These hosted-service notices did not fail the release workflows and are retained for future CI maintenance rather than changing the immutable candidate.

## Publication verification

- Candidate commit and tag target: `43b2465afc23b0684399869aa133d410bb1398eb`, immutable tag `v1.0.51`.
- [Development1 Android native-compatibility workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36653318185): passed in 6m56s on the candidate commit, including the Android 15 16 KB emulator smoke.
- [Development1 course workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36653318089): passed in 8m28s on the candidate commit.
- [GitHub installer release workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36654058485): passed in 15m08s.
- [npm trusted-publish workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36654089435): passed in 11m05s and published signed provenance to [Sigstore](https://search.sigstore.dev/?logIndex=3009461895).
- [Immutable-tag course workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36654110531): passed in 6m23s.
- [GitHub release v1.0.51](https://github.com/OpenARDF/Radio-Oracle/releases/tag/v1.0.51): public, non-draft, non-prerelease, and targeted at the candidate commit. All 14 assets were downloaded into a fresh temporary directory and matched GitHub's SHA-256 digests. The GitHub-safe package archive contains version 1.0.51, candidate commit metadata, and all six Linux, macOS, and Windows x64/ARM64 runtime jars; its SHA-256 is `1a081d3d8a2931ff6c2671d12577b2babe373ccf35e6433a4f7a048ec196a36f`.
- The README installer link remains `https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle`.
- [npm package 1.0.51](https://www.npmjs.com/package/@openardf/radio-oracle/v/1.0.51): public with `latest` set to 1.0.51, SLSA provenance attached, and source commit `43b2465afc23b0684399869aa133d410bb1398eb`. Registry shasum: `cb7ccae43bbb8e5faf71240303bbbd332ceef9a4`; integrity: `sha512-ymS/PHPuVXsZ4YckRgxBo/24aCLeQ+zNgVqNmPJaSVBCnjNZ7UG9xK0vVUat8h6+y49vySh9Rfe2UUvv0YHD5Q==`.
- The exact public tarball returned HTTP 200. A fresh temporary-directory registry install launched Radio-Oracle successfully, and its targeted cleanup removed only the smoke instance; the separately running user app remained active.

npm accepted the trusted publish at 01:24 UTC and made the 119.4 MB package publicly downloadable at 01:41 UTC. The release process waited through asynchronous processing and did not attempt a duplicate publish.

## Physical acceptance

The release code passed physical Galaxy Tab A7 Lite startup, SPORTident connection, card read/store, disconnect, and crash-buffer acceptance before the release-only version and documentation update. No runtime code changed afterward.

## Explicitly unverified scope

Google Play submission, printer output, and manual Windows/Linux UI acceptance are not part of this requested jDeploy deployment and Android build. They are not reported as passing.
