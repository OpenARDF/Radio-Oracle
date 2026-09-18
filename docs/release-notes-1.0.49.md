# Radio-Oracle 1.0.49

## Desktop and shared changes

- Standardize native CSV exports with column headers, comma separators, and quoted fields. Continue importing legacy semicolon files and preserve dedicated compatibility export dialects.
- Explain CSV formats beside import/export actions and offer import templates. Add separate KML/KMZ guidance explaining Google Earth compatibility, control placemarks, regulation categories, and route lines.
- Display course reports in Setup > Courses, including elevation profiles with control markers. Fit the 2D diagram and elevation profile side by side when the window is wide enough.
- Hide Mandatory and Phantom route-point markers and labels in 2D diagrams while retaining their effect on the route line.
- Review XML control mappings before import, with public names, roles, and SI station numbers prefilled from source data and existing controls. Correct mappings before accepting the course import.
- Apply the same recorded-readout restriction to Controls CSV imports as to other course imports, and group Course Tools under Setup > Courses.

## Android release notes

CSV exports now use consistent comma-separated columns with headings and correct quoting. Import and export dialogs explain the CSV format, and import templates help you organize your data. Existing semicolon-separated CSV files remain supported.

## Release validation

Charles Scharlau authorized the full jDeploy deployment and waived hardware tests only. Physical SPORTident, printer, and device acceptance checks were skipped at his request, not reported as passing. Full regression, course-transfer, schema, packaging, signing, publication, and public-download verification completed. See [deployment evidence](release-verification-1.0.49.md).

The full local regression round passed: 703 shared tests on desktop, the same 703 on Android debug and release, 1,056 desktop tests, and 322 Android app tests. Five optional desktop archive fixtures and one Android transfer test without its external input were skipped in the broad run; the Android transfer test subsequently ran and passed in the explicit plain/protected desktop–Android archive-transfer gate. Shared smoke, Android debug build, IOF schema/fixture validation, workflow lint, and secret scanning also passed. Full-suite XML reports were preserved before the filtered transfer checks.

Plain/protected archive transfers and their returned-desktop checks, desktop runtime/distributable verification, jDeploy release preflight and package preview, and local jDeploy installation/launch smoke passed. The signed Android bundle verifies as 1.0.49/code 57 with the previous release signing certificate. Both publishing workflows and the immutable-tag course workflow passed. All 14 public GitHub assets matched their published sizes and hashes; both public package archives verified, and the public npm install/launch smoke passed.

Android version name is 1.0.49 and version code is 57. Android store submission is separate from this desktop jDeploy deployment.

## Standards compatibility

Deployment drift review: **compatible**. Reviewed the current upstream `AROB-CR/radio-o-standards` README, ARDF JSON schema/examples, and ARDF XML schema/examples at commit `83eeaa413c6b0b7a0564247b45a1d93cfd5fc3c1`. ARDF JSON and IOF XML exports, namespaces, scoring semantics, and Race File schema remain unchanged. XML control IDs remain source identifiers; reviewed public names and SI station bindings are internal import mappings, using schema-valid IOF Id/Name/PunchingUnitId fields without adding IOF core elements. Native CSV changes preserve old-file import support and dedicated third-party dialects; they do not change the upstream ARDF JSON/XML contract. Existing standards differences are not expanded by this release.

The shared smoke fixture was updated to current canonical control storage and comma-separated CSV expectations; no application behavior changed in that release-gate repair.

The separate course-workflow CI setup now requests platform-tools instead of the removed Android tools package. The repository's Android 37.0 platform and its regression and archive-transfer checks remain enabled.
