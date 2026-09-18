# Radio-Oracle 1.0.49 deployment evidence

Release candidate: `bbee63fd6fe14b95114da91c4072494e1985e31f`; immutable tag: `v1.0.49`.

## Regression and compatibility

- Full local regression passed: 703 shared tests on desktop, the same 703 on Android debug and release, 1,056 desktop tests, and 322 Android app tests. There were no failures or errors.
- Five fixture-dependent desktop tests and one Android archive-transfer test were skipped in the broad run. Both desktop-return and Android archive-transfer tests subsequently ran and passed with generated inputs in the explicit plain/protected transfer lane. The remaining four private-data fixture tests were not activated by the standard regression command.
- Shared smoke, Android debug build, IOF schema/fixture validation, workflow lint, and secret scans passed. The smoke fixture was corrected to current canonical control storage and comma-separated CSV expectations without changing application behavior.
- Plain and protected desktop–Android archive round trips passed, including retained course designs and recorded punches.
- Standards drift outcome: **compatible**, reviewed against current upstream `AROB-CR/radio-o-standards` commit `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1`. See [release notes](release-notes-1.0.49.md) for the rationale.

## Packaging and publication

- Desktop runtime and native distributable verification passed.
- jDeploy release preflight, package preview, and local installation/launch smoke passed with unsuffixed version 1.0.49.
- [GitHub release](https://github.com/OpenARDF/Radio-Oracle/releases/tag/v1.0.49): stable, published, 14 assets.
- [Installer workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/35293908195): passed, including desktop/Android regressions and packaging gates.
- [npm workflow](https://github.com/OpenARDF/Radio-Oracle/actions/runs/35293912344): passed, including desktop/Android regressions, preflight, package preview, and OIDC Trusted Publishing.
- [Course-workflow verification on the immutable tag](https://github.com/OpenARDF/Radio-Oracle/actions/runs/35294455166): passed, including regression, lifecycle reports, and the complete Android Room/archive return path.
- The original [branch course-workflow run](https://github.com/OpenARDF/Radio-Oracle/actions/runs/35293908182) passed regression and lifecycle checks but was canceled during transfer by a concurrent roadmap-only branch update. It is not reported as a completed pass; the immutable-tag run supplies the complete verification.

## Public-download verification

- All 14 assets were freshly downloaded without authentication. Each byte count and SHA-256 matched its published GitHub metadata.
- Fresh GitHub and npm package archives report plain version 1.0.49 and contain all six macOS, Windows, and Linux architecture runtimes. Both contain the XML mapping review, elevation-profile UI, and CSV guidance classes.
- npm SHA-512 integrity, exact tagged source commit, and provenance metadata verified. Registry processing delayed initial availability; the package was not republished.
- The standard public npm installation-and-launch smoke passed for `@openardf/radio-oracle@1.0.49` after the archive became available.
- The [public desktop installer page](https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle) is available.

## Android

Version name 1.0.49; version code 57. Bundle: `app/build/outputs/bundle/release/app-release.aab`. Its manifest versions and JAR signature verify, and its signing certificate matches the previous release.

Bundle SHA-256: `e5e0769b1305b2ccb996fe043cde6202500c7a32b3d2a38035af3c1dc085495f`.

Android store notes: CSV exports now use consistent comma-separated columns with headings and correct quoting. Import and export dialogs explain the CSV format, and import templates help you organize your data. Existing semicolon-separated CSV files remain supported.

Android store submission is separate from this desktop jDeploy deployment.

## Hardware waiver and local evidence

Charles Scharlau explicitly waived hardware tests for this deployment. Physical SPORTident, printer, and device acceptance checks were skipped at his request and are not claimed as passed. Manual Windows/Linux UI acceptance was not performed; the CI/software checks and packaged runtime inventory provide the cross-platform evidence recorded above.

- Local summary: `desktopApp/build/reports/release-1.0.49/verification.json`
- Preserved full-suite results: `desktopApp/build/reports/release-1.0.49/regression-counts.json` and `regressions/`
- Preserved transfer reports: `desktopApp/build/reports/release-1.0.49/course-workflow/`
- Fresh public assets and verification record: `/private/tmp/radio-oracle-public-1.0.49/`
- Gate logs: `/private/tmp/radio-oracle-1.0.49-*.log`
