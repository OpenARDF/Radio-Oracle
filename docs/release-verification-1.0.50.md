# Radio-Oracle 1.0.50 release verification

## Local candidate gates

| Gate | Result |
| --- | --- |
| Shared desktop, Android debug/release shared, desktop app, and Android debug/release unit suites | 4,101 tests; 0 failures; 0 errors; 7 skips |
| Android artifacts | Debug APK assembled; signed release AAB built; release lint passed |
| Desktop artifacts | Runtime, native distributable, and six-jar jDeploy bundle verified |
| Course workflow | Desktop flow 16/16, Android transfer 2/2, desktop-Android-desktop round trip 3/3 |
| IOF XML | Bundled examples and focused shared, Android, and desktop schema checks passed against IOF 3.0 |
| jDeploy | Release preflight, package dry run, macOS ARM install verification, and isolated launch smoke passed |
| Dependencies and static checks | npm audit: 0 vulnerabilities; actionlint, shellcheck, shfmt, diff check, function-size audit, and diff secret scan passed |
| Standards drift review | Compatible with `AROB-CR/radio-o-standards` commit `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1` |

The preserved unfiltered test XML is under `desktopApp/build/reports/release-1.0.50/regressions/` in the release workspace.

## Android release bundle

- Path: `app/build/outputs/bundle/release/app-release.aab`
- Version name: 1.0.50
- Version code: 58
- SHA-256: `559153d8da04af1cd7b7856119219b9a93eb84c90da3ca0c6843fa43629a5b85`
- Signature: verified
- Upload certificate owner: `CN=Radio-Oracle Android Upload, OU=OpenARDF, O=OpenARDF, C=US`
- Upload certificate SHA-256: `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA`

The upload certificate is intentionally self-signed. Java's ordinary JAR-signature verification passes; its strict public-chain check is not applicable to this private Android upload key.

## Documented toolchain notices

- Android Gradle Plugin 8.13.2 reports that its published compatibility testing ends at SDK 36.1 while Radio-Oracle intentionally compiles against SDK 37. No suppression was added; debug/release compilation, lint, signing, and unit suites passed.
- Android packaging cannot strip symbols from prebuilt Firebase Crashlytics and AndroidX DataStore native libraries. They are packaged unchanged, as reported by AGP.
- The current jDeploy toolchain still installs its own deprecated `shelljs` 0.8, `glob` 7, and `inflight` development dependencies. Radio-Oracle overrides vulnerable `brace-expansion` with 1.1.21, updates bundled `tar` to 7.5.22, keeps a valid npm dependency tree, and has zero reported npm vulnerabilities.
- JVM test workers report that class-data sharing is unavailable when their bootstrap classpath is modified. This is a test-VM runtime notice, not a source warning or test failure.
- GitHub reports that `actions/setup-node@v4`, `actions/upload-artifact@v4`, `android-actions/setup-android@v3`, and `xresloader/upload-to-github-release@v1.6.0` still target Node 20 and are being forced onto Node 24. It also announced a future `ubuntu-latest` image migration. These hosted-action notices did not fail the release workflows and are retained for future CI maintenance rather than changing the immutable release candidate.

## Publication verification

- Candidate commit and tag target: `34118167de62e1292467fe44f2f8c40626c7b858`, immutable tag `v1.0.50`.
- [GitHub installer release workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36507304705): passed in 12m43s.
- [npm trusted-publish workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36507326003): passed in 11m24s and published signed provenance to [Sigstore](https://search.sigstore.dev/?logIndex=2991460459).
- [Immutable-tag course workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36507328187): passed in 8m32s.
- [Development1 course workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/36507271258): passed on the same candidate commit.
- [GitHub release v1.0.50](https://github.com/OpenARDF/Radio-Oracle/releases/tag/v1.0.50): public, non-draft, non-prerelease, and targeted at the candidate commit. All 14 assets were downloaded into a fresh temporary directory and matched GitHub's SHA-256 digests. The GitHub-safe package archive contains version 1.0.50 and all six Linux, macOS, and Windows x64/ARM64 runtime jars; its SHA-256 is `c08cb6a39cb0bfbe8ca3eb5d9d32bcccaa1225ec55eae8000d0e3db82ee09d35`.
- [npm package 1.0.50](https://www.npmjs.com/package/@openardf/radio-oracle/v/1.0.50): public with `latest` set to 1.0.50. Registry shasum: `60c299c590f7ead27eca249b177e9c8ff04d7c2d`; integrity: `sha512-2KWqc+zcCHXc4TP62EmL2GP6A0HAi0kfNNTw4jWH3Z0/Ffpcj4NSd8PJHMAlg8duM0SKUr9wjLv80YBAG73PCg==`.
- A fresh temporary-directory install from the public npm registry launched Radio-Oracle successfully. Its targeted cleanup removed only the smoke instance; the separately running user app remained active.

npm accepted the trusted publish at 01:30 UTC but held the 119.3 MB package in its asynchronous processing queue until 02:26 UTC. The release process did not issue a duplicate publish; public metadata, dist-tag, tarball, provenance, and fresh install were verified after processing completed.

## Explicitly unverified scope

Physical SPORTident hardware, printer output, installation on an Android device, Google Play submission, and manual Windows/Linux UI acceptance were not part of this requested jDeploy deployment and Android build. They are not reported as passing.
