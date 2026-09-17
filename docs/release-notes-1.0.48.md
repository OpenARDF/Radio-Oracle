# Radio-Oracle 1.0.48

## Desktop and shared changes

- Assign an existing course to one or more categories directly from Categories. Course Analyzer remains optional.
- Preserve existing Beacon and Spectator settings when reimporting course files that do not specify those roles.
- Apply calculated courses without changing fox numbers. Renumbering now has its own review and confirmation, showing affected courses and station numbers.
- Optionally import Condes points numbered 900–999 as route points without punches. This option is off by default.
- Show fox-count guidance as a warning for Practice events while keeping essential course checks.
- Improve course-report warnings and explanations, and add diagnostic logging for imports, assignments, and course application.

## Android release notes

Maintenance release to keep Android and desktop versions aligned. There are no Android feature changes in this update.

## Release validation

Charles Scharlau authorized the full deployment and explicitly waived additional tests for this release. The completed implementation validation is recorded in [Course import and numbering validation](course-import-and-numbering-validation.md): 681 shared, 1,031 desktop, and 316 Android tests passed, followed by successful plain and protected desktop–Android transfer checks and supplied-file replay. These results precede the version-only release preparation. Windows UI and physical SPORTident/device testing were not performed and are covered by the waiver; skipped checks are not claimed as passed.

Release packaging, version alignment, publication, and public-download verification remain part of deployment. Existing publication workflows retain their built-in gates. Android version name is 1.0.48 and version code is 56. The jDeploy workflow publishes desktop installers and packages; the Android bundle is prepared separately for the release operator.
