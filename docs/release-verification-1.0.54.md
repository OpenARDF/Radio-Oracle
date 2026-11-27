# Radio-Oracle 1.0.54 release verification

## Candidate checks

| Suite | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| Shared Kotlin on desktop | 774 | 0 | 0 | 0 |
| Shared Kotlin on the Android host | 774 | 0 | 0 | 0 |
| Desktop application | 1,196 | 0 | 0 | 5 |
| Android application | 349 | 0 | 0 | 1 |
| Total | 3,093 | 0 | 0 | 6 |

The desktop–Android–desktop course transfer passed with plain and encrypted archives. IOF XML schema checks passed. The desktop navigation audit reported zero issues. Release lint found zero errors and 19 dependency or tool version notices covered by the existing maintenance policy. The npm audit found zero vulnerabilities. The release-script tests, actionlint, shell checks, redacted secret scan and diff checks passed.

The desktop distributable and all six packaged desktop runtimes passed verification. The npm package preview was 118.6 MB. The native macOS ARM64 installer and isolated installed-app launch passed. The release owner waived further physical Android and SPORTident testing for this deployment.

The standards review outcome was **standards-pr-linked**. The proposal defines how the optional `category_open` Boolean preserves a mixed-gender category in ARDF JSON. [Upstream proposal](https://github.com/AROB-CR/radio-o-standards/pull/9)

## Signed Android bundle

| Property | Verified value |
| --- | --- |
| Package | `org.openardf.radiooracle` |
| Version | 1.0.54 (62) |
| Bundle SHA-256 | `308190335a40906aaa6caa9d37599acabc0ee3ea1a73d82b2e0b623d376ef05b` |
| Upload certificate SHA-256 | `B1:0A:F4:2A:4A:61:64:23:84:CA:A4:8B:2E:43:9E:6D:22:16:23:11:0F:71:DB:A2:D7:89:20:22:57:DD:72:DA` |

The bundle signature verified. The packaged manifest excludes both advertising-ID permissions. The bundle requests 16 KB native-library packaging, and all load segments in both 64-bit libraries meet that alignment.

## Hosted checks

The release candidate passed all six hosted jobs. The merged release commit `554880d0e46e61d7c1e9d95c9c242bcaffa9a50f` has the same source tree as the validated candidate and carries the annotated tag `v1.0.54`. The installed-package checks cover x64 and ARM64 on both Linux and Windows. [Release pull request](https://github.com/OpenARDF/Radio-Oracle/pull/34)

| Checks on the tagged commit | Result |
| --- | --- |
| Linux installed packages | [Passed](https://github.com/OpenARDF/Radio-Oracle/actions/runs/46011057171) |
| Windows installed packages | [Passed](https://github.com/OpenARDF/Radio-Oracle/actions/runs/46011057049) |
| Course workflows | [Passed](https://github.com/OpenARDF/Radio-Oracle/actions/runs/46011057088) |
| Android bundle and 16 KB emulator | [Passed](https://github.com/OpenARDF/Radio-Oracle/actions/runs/46011057151) |

## Public desktop distribution

GitHub published 1.0.54 as the latest release with installers for macOS, Windows and Linux on x64 and ARM64. All 14 assets were downloaded and their SHA-256 hashes matched GitHub's recorded digests. The 118,629,822-byte GitHub source package had SHA-256 `5d08dcc0492d2ba01276dd3f58daf6977a387ca487c4ad093440dd2739760c92`. Its package metadata identifies Java 17, jDeploy 6.1.10, tag `v1.0.54` and the tagged commit. [GitHub release](https://github.com/OpenARDF/Radio-Oracle/releases/tag/v1.0.54) · [Installer workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/46011101918)

The npm registry lists `@openardf/radio-oracle@1.0.54` as `latest`. Its SLSA provenance names the tagged commit and the repository's trusted publishing workflow. [npm package](https://www.npmjs.com/package/@openardf/radio-oracle/v/1.0.54) · [Publishing workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/46011141553) · [Sigstore entry](https://search.sigstore.dev/?logIndex=4149751823)

The downloaded npm tarball matched the registry's SHA-1 `f1fa37557a3b77b23e740c3a3fcf239b2535b200` and integrity `sha512-MKll3HXuC5Bk+9G2KKCZxZJn3YhZUv4Tru+NU0yPn4PuI9hJmtb4TtMLvJjajPEFKgOXEU1NBBmMpUXujjP7uA==`. A fresh install from npm in an isolated directory launched Radio-Oracle with the sample race project.

The README links to the [public desktop install page](https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle). Both copies of the jDeploy package index identify 1.0.54 as the latest version and match the index in the GitHub release.

## Google Play

The OpenARDF Internal testing track shows **62 (1.0.54) available to internal testers** as of November 27, 2026 at 9:02 a.m. EST. Play accepted the signed bundle and the English release notes for this version. [Play Console release](https://play.google.com/console/u/1/developers/7559128284059430620/app/4974880781425514065/tracks/4701437895956528564/releases/12/details)

Play lists minimum API 26, target SDK 37 and four ABIs. It reported 18,227 supported devices and zero additions or removals compared with 1.0.53. Play warned that native debug symbols were missing for bundled third-party AndroidX DataStore code. The existing release waiver covers this warning. Missing symbols limit diagnostics for native crashes in that library.

The production dashboard still requires completion of the store listing and a closed test with at least 12 testers continuously opted in for 14 days. At publication it showed zero closed-test opt-ins and a disabled production application.
