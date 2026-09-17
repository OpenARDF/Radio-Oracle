# Radio-Oracle 1.0.48 deployment evidence

Release candidate: `7de8a67388d8008a201c1ddf07276c611b1595b0`; immutable tag: `v1.0.48`.

## Publication

- [GitHub release](https://github.com/OpenARDF/Radio-Oracle/releases/tag/v1.0.48): published, stable, 14 assets.
- [Installer workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/35179298639): passed, including its built-in regression and packaging gates.
- [npm workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/35179298688): passed, including its built-in regression and packaging gates; registry metadata identifies the exact candidate and includes provenance.
- All 14 GitHub assets were freshly downloaded and matched their published byte counts and SHA-256 digests. The downloaded application jar reports plain version 1.0.48; its package includes the six macOS, Windows, and Linux architecture runtimes.
- [Public desktop installer page](https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle): available.
- The npm archive became available after a propagation delay. Its SHA-512 integrity, version, exact source commit, provenance, and six native runtimes were verified from a fresh download. The standard registry install-and-launch check passed for `@openardf/radio-oracle@1.0.48`.

## Android

Version name 1.0.48; version code 56. Release bundle built at `app/build/outputs/bundle/release/app-release.aab`. Its signature verifies and matches the existing Android release certificate. The bundle manifest contains 1.0.48. Android store submission is separate from this desktop jDeploy deployment.

Bundle SHA-256: `dd5007a475921af27d30feae5ba2456c832eaf66b357b898d3ffdb7aa7ff80df`.

Android release notes: Maintenance release to keep Android and desktop versions aligned. There are no Android feature changes in this update.

## Waiver and limitations

Charles Scharlau explicitly waived additional tests for this release. No additional local regression suite was required; local release preflight, packaging, signing, and public-artifact checks were performed. The existing publishing workflows retained and passed their built-in regression gates. Prior implementation validation is recorded in [Course import and numbering validation](course-import-and-numbering-validation.md).

The separate [course-workflow reliability run](https://github.com/OpenARDF/Radio-Oracle/actions/runs/35179287023) failed during Android SDK setup before tests, with the same `sdkmanager` failure as the prior release run 34920923245. This remains a validation gap under the explicit waiver, not a passing test result. Windows UI and physical SPORTident/device testing were not performed.

## Local evidence

- Build and signing summary: `desktopApp/build/reports/release-1.0.48/verification.json`
- Downloaded public assets and verification records: `/private/tmp/radio-oracle-public-1.0.48/`
- Release build log: `/private/tmp/radio-oracle-1.0.48-build.log`
- Release preflight log: `/private/tmp/radio-oracle-1.0.48-preflight.log`
