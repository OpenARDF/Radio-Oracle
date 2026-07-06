# Desktop Preparation Notes

These notes define the first desktop target before a user-visible desktop app is
added. They keep the desktop effort focused on a small race-admin beta instead
of full Android parity.

## Desktop Beta Boundary

The first desktop app should be a thin UI over shared race-domain services. It
should support race administration workflows that are useful away from the
finish table:

- create, open, edit, save, and export race files;
- manage races, categories, control points, aliases, competitors, readouts, and
  results;
- manually enter or edit readout-equivalent punch data;
- download one SI5/SI6/SI8/SI9/SIAC card at a time, or run an experimental
  continuous readout loop, from an attached SPORTident station that is already
  configured in READOUT/SI MASTER mode;
- choose whether duplicate SI-card reads are ignored, replaced, or stored as
  new readouts;
- show the most recent readout SI card, competitor, status, timestamp, and
  warning/error state;
- play optional desktop alert sounds for duplicate or error/unknown SI readouts;
- inspect competitors in drawn start-time order in a Start List desktop view;
- draw start times by category with club rotation and a configurable interval;
- assign unmatched readouts to competitors;
- mark competitors DNS without an SI-card readout;
- preview shared finish-ticket text for readouts and print the previewed ticket
  through desktop system printing;
- summarize live-result send readiness, including unsent matched results and
  skipped readouts, and export Android-shaped live-result and final-result JSON
  payloads for provider inspection;
- manually send unsent matched live results to ROBIS using the race API key;
- send unsent matched ROBIS live results in the background when enabled in
  Settings;
- show desktop system-printer readiness in Settings, including the selected
  print target and detected system printers;
- serve auto-refreshing loopback-only local result/category/start-list/in-forest
  displays plus `/results.json`, `/categories.json`, `/starts.json`, and
  `/in-forest.json` endpoints from the open desktop Race File;
- track started competitors without readouts in an In Forest desktop view;
- recalculate results using shared services;
- import/export supported race and result formats as they move into shared
  code.

macOS hardware validation and Windows packaged-app smoke validation are beta
release blockers. Linux packaging is best-effort for this beta. The release
checklist lives in [`desktop-beta-checklist.md`](desktop-beta-checklist.md).

The desktop beta should not include:

- Bluetooth printer transport;
- Android Room database migration or shared SQL persistence;
- SPORTident station write/reprogramming actions;
- multi-download-station support; the beta may read multiple cards from one
  attached SPORTident download station, but not coordinate multiple connected
  stations;
- unverified OCheckList/new-card imports;
- LAN-exposed local web displays;
- any promise that desktop can replace the Android race-day download workflow.

## Current Decision Boundary

The current desktop beta decisions are:

- Keep the local result/start/in-forest server loopback-only. LAN exposure needs
  a separate hardening decision covering bind address, operator warnings, and
  race-network security.
- Use desktop system printing as the first beta printer transport. The WiFi
  Epson target is visible to Java/CUPS as `EPSON ET-2720 Series`; the DYMO
  system printer is not a Radio-Oracle target. ARDFEvent supports serial,
  native USB, and dummy ESC/POS desktop printer transports, but not Bluetooth;
  keep desktop Bluetooth printing as a post-beta transport adapter unless a
  reliable JVM/macOS pairing path is selected. Android Bluetooth ESC/POS
  printing remains pre-beta work for the Android app using the available
  Bluetooth printer hardware.
  The readout ticket preview includes a `Print` action that submits the shared
  ticket text through this system-printer path.
- Keep OCheckList/new-card import out of the beta until a verified sample file
  or schema is available.
- Keep shared SQL persistence post-beta. The beta uses `.rom.json` files while
  shared domain services and import/export APIs stabilize.
- Keep SPORTident station reconfiguration writes disabled until each
  configuration-write transaction is verified on real hardware and protected by
  confirmation plus immediate read-back validation. The Time Sync write path is
  the first exception, limited to the capture-proven `F6`/`F9` sequence and an
  awake coupled target station.

## Storage

Use file-backed Race File storage for the beta, most likely a `.rom.json`
Race File. Shared SQL remains post-beta. A later SQL spike should compare Room KMP as
the baseline candidate against SQLDelight as the fallback/comparison option.

## UI And Module Shape

Prefer a small desktop app module that depends on `:shared`. Compose
Multiplatform Desktop is the default UI candidate because it keeps the app in
Kotlin and aligns naturally with the existing Kotlin Multiplatform foundation.

ARDFEvent is the closest verified desktop reference for mature race-day ARDF
workflows. Use it as a functional benchmark for desktop ergonomics: dense
task-specific pages, operator-visible status, fast table editing, direct
print/send/readout actions, and a separate readout status surface that can stay
visible during finish work. Do not copy ARDFEvent's Python/PyQt architecture or
plugin system into Radio-Oracle unless a future integration creates a concrete
need; keep Radio-Oracle centered on shared Kotlin services and the existing
Android-compatible race model.

ARDFEvent-derived desktop parity gaps to track after the current live-result
slices:

- richer ARDFEvent-style start-list draw setup with configurable draw rows;
- desktop system-printer ticket output using the shared finish-ticket renderer
  as the text source, with desktop Bluetooth printer transport as a post-beta
  adapter;
- OCheckList-style new-card import or equivalent race-day status import;
- LAN-facing finish-line web display/API hardening for results when mobile data
  is not available.

The desktop app should intentionally feel like the Android app, not like a new
product. Reuse the Android visual language where practical:

- preserve the same primary workflows and vocabulary: races, categories,
  competitors, readouts, results, aliases, and settings;
- reuse or port the existing Android vector icons for matching actions and tabs;
- use the Android theme colors as the starting desktop palette, including
  primary purple, secondary teal, white/black text defaults, grey disconnected
  state, orange reading state, green connected/read state, yellow warning state,
  and red error state;
- mirror the Android status-strip behavior for SI/readout state, even when the
  beta desktop app only shows simulated or manually entered readout state;
- keep dialogs, table rows, edit forms, and result/readout status colors close
  enough that Android users recognize the desktop screens immediately.

Desktop ergonomics can adapt to larger screens, menus, keyboard shortcuts, and
resizable windows, but those adaptations should extend the Android interface
rather than inventing a separate desktop visual identity.

The provisional desktop navigation direction is workflow-first and should be
tested with real race use before treating it as a fixed design. Keep four
top-level workflow groups visible along the bottom of the desktop app, echoing
Android bottom navigation:

- Setup;
- Race Operations;
- Results/File Export;
- Help/About/App Settings.

Selecting a workflow group should change the left-side navigation to show
task-specific items for that workflow. Lower-level items may replace the
left-side navigation with one submenu level, but that submenu must include a
visually distinct Back button that returns to the previous menu. The bottom
workflow navigation should remain available as the user's return-to-top path.
Use breadcrumbs or equivalent context text, such as
`Setup > Categories`, so testers can tell where they are.

Initial placement guidance:

- Setup: Race File commands, race details, categories, competitors,
  aliases/control names, start-list setup, setup imports, setup exports, and
  Race File diagnostics/validation under a Utils item.
- Race Operations: SI readout, continuous readout, unmatched readouts,
  competitor race-day status, in-forest monitoring, finish tickets, download
  station status, and printer readiness.
- Results/File Export: results review, manual result status edits, live/local
  result display, ROBIS sending, result exports, JSON/XML/TXT/HTML exports, and
  archival Race File copy export.
- Help/About/App Settings: settings that affect app behavior, such as alias
  display, race-discipline mode, duplicate-SI policy, readout alert sounds,
  hardware/app preferences, logs, beta scope, help, and about information.

This grouping is a beta UX hypothesis. If an action does not fit naturally in
one of these workflows, treat that as evidence that the action name, behavior,
or location needs more product review rather than forcing the action into the
nearest bucket.

The desktop app should keep platform concerns thin:

- file pickers and local filesystem permissions in the desktop module;
- race validation, formatting, placement, and import/export policy in shared
  code;
- desktop-only diagnostics and packaging metadata outside Android code.

## Packaging Direction

Use a jDeploy-based packaging path unless a focused packaging spike finds a
concrete blocker. This mirrors the SerialSlinger approach and should make
release operations familiar.

Packaging should eventually provide:

- launchable desktop artifacts for macOS, Windows, and Linux;
- version/build metadata tied to Git tags;
- a repeatable package validation command;
- a packaged-app smoke scenario that opens, edits, saves, reopens, and exports a
  sample race.

Keep Hydraulic Conveyor as a comparison option if jDeploy cannot satisfy a
specific requirement. Keep raw `jpackage` as a low-level fallback, not the
preferred release workflow.

### Local Packaging Environment

Desktop packaging should run with a registered JDK 17. On macOS, Eclipse
Temurin 17 is the recommended default because it is a standard, signed JDK
distribution and registers cleanly with `/usr/libexec/java_home`.

Verify the installed JDKs with:

```shell
/usr/libexec/java_home -V
```

Then run desktop packaging commands from a shell that selects JDK 17:

```shell
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
```

The current desktop packaging smoke checks are:

```shell
./gradlew :desktopApp:checkRuntime
./gradlew :desktopApp:createDistributable
./gradlew :desktopApp:verifyDesktopDistributable
./gradlew :desktopApp:prepareDesktopJdeployBundle :desktopApp:verifyDesktopJdeployBundle
```

On macOS, `scripts/desktop-distributable-prepare.sh` selects the registered JDK
17 runtime when available and runs the local app-image packaging smoke. The
npm-facing `scripts/jdeploy-prepare.mjs` selects `gradlew` or `gradlew.bat`,
adds `JAVA_HOME/bin` to `PATH`, and runs the two Gradle-side jDeploy bundle
tasks.

`checkRuntime` verifies that Compose Desktop can find a usable JDK runtime.
`createDistributable` currently writes the macOS app image to
`desktopApp/build/compose/binaries/main/app/Radio-Oracle.app`. The Compose
Desktop metadata now sets the app image name, description, vendor, macOS bundle
ID, and native-package version. Android, npm, jDeploy jar metadata, and native
desktop app images use the same product version, starting at `1.0.0`. Future
releases increment only the third, rightmost field: `1.0.1`, `1.0.2`, and so on.

`packageDistributionForCurrentOS` also passes in the current module shape, but
the app-image output from `createDistributable` is the clearest local smoke
signal until installer packaging metadata is finalized.

`prepareDesktopJdeployBundle` stages an executable desktop jar plus runtime
libraries under `desktopApp/build/jdeploy`. `verifyDesktopJdeployBundle` checks
the jar manifest and staged classpath layout that the future jDeploy package
metadata will consume. Because the same jDeploy package is used to create
Windows, Linux, and macOS launchers, the staged bundle must include Compose
Desktop/Skiko native runtime jars for Intel x64 and ARM64 targets on all three
operating systems, not only the native runtime for the machine that publishes
the release.

The selected public jDeploy/npm package identity is `@openardf/radio-oracle`.
End users should install the desktop beta from the jDeploy GitHub release page:

```text
https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle
```

This page follows GitHub release installer assets created by
`.github/workflows/jdeploy-github-release.yml`. That workflow rewrites the
temporary GitHub-release jDeploy package identity to unscoped `radio-oracle`
before publishing installer assets, because jDeploy's GitHub-release launcher
downloads release tarballs by package name and scoped npm names contain a slash
that cannot be used as a GitHub release asset path. The committed npm package
identity remains `@openardf/radio-oracle`.

The npm package remains useful for registry smoke testing and direct CLI-style
installs, but the jDeploy GitHub page is the preferred user-facing desktop
install path.

The package tarball must bundle `node-fetch`, `shelljs`, `tar`, and `yauzl`.
The generated `jdeploy.js` launcher needs those runtime modules when a
GitHub-release install runs from the downloaded tarball rather than from an npm
install directory.

For every jDeploy deployment, keep both paths active unless workflow
maintenance becomes a practical burden. The GitHub-release jDeploy page is the
public end-user install method; npm publishing remains the
registry/provenance/automation path. A deployment is not complete until the
GitHub release installer workflow has produced the user-facing release assets
and the npm Trusted Publishing workflow has published the matching package
version or the release notes explicitly record why one path was intentionally
skipped.

Local metadata checks are available through:

```shell
npm install
npm run jdeploy:pack-preview
npm run jdeploy:local-smoke
npm run jdeploy:release-preflight
npm run jdeploy:registry-smoke -- <version>
```

`npm run jdeploy:pack-preview` prepares the Gradle-side jDeploy bundle, runs
`jdeploy package`, and shows the npm tarball payload without publishing. A real
publish is guarded by `scripts/check-jdeploy-publish.mjs` and requires
`RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH=1`.

`npm run jdeploy:local-smoke` installs the package locally, verifies the
generated app bundle or Windows executable, launches it with a temporary copy of
`samples/desktop-smoke.rom.json`, confirms the process starts, and quits the
app.

`npm run jdeploy:release-preflight` checks package identity and version
alignment before any intentional publish. It also runs the Gradle jDeploy
bundle verification, which fails if any required Windows, Linux, or macOS Skiko
runtime jar is missing from the staged classpath.

`npm run jdeploy:pack-preview` and `npm run jdeploy:release-preflight` both
generate and inspect desktop jDeploy metadata. Run them serially. The npm
scripts use `scripts/jdeploy-exclusive.mjs` to serialize accidental concurrent
local runs, but release operators should still avoid starting those commands in
parallel.

Full release builds must use an unsuffixed desktop display version that matches
the npm/jDeploy package version. Set `RADIO_ORACLE_RELEASE_BUILD=1`, or pass
`-PradioOracle.releaseBuild=true` to Gradle, when preparing a publishable
jDeploy package. Release preflight sets this flag automatically and verifies
that the generated jDeploy jar contains the plain base version rather than an
iterative desktop test suffix.

Interactive desktop test builds append an alphabetic suffix to the numeric base
version, such as `1.0.7a`, `1.0.7b`, through `1.0.7z`, then `1.0.7aa`,
`1.0.7ab`, and so on. When the numeric base version changes, the desktop test
suffix resets to `a` for that new base version.

After a public npm publish, `npm run jdeploy:registry-smoke -- <version>`
installs that exact registry version in a temporary directory, launches it with
the smoke Race File, confirms startup, and quits the app.

If a release gate fails during deployment and a fix is made before publishing,
do not treat the fix as automatically safe just because the failing command now
passes. First classify the patch's regression risk:

- Low-risk release plumbing or documentation fixes may continue after the
  original failing gate, the relevant focused test, and the normal jDeploy
  preflight/smoke checks pass.
- Runtime, shared-model, import/export, scoring, hardware, packaging, or
  platform-behavior fixes are significant-risk deployment fixes. Postpone the
  deployment unless the fix has an explicit regression test for the failure, the
  affected nearby tests pass, and the broad desktop/Android release gates have
  been rerun.
- If the needed regression evidence requires hardware or a platform that is not
  available, either record an explicit user-approved waiver in the release notes
  or postpone the deployment. Do not spot-fix a release blocker and publish on
  the strength of one narrow passing command.

The normal deployment sequence is:

1. Bump Android, desktop, and npm/jDeploy versions together.
2. Run `npm run jdeploy:release-preflight`.
3. Merge the release state to `main`.
4. Push the matching `v<version>` tag and let
   `.github/workflows/jdeploy-github-release.yml` publish GitHub release
   installer assets.
5. Run `.github/workflows/publish-jdeploy.yml` in `publish` mode for the same
   version.
6. Run `npm run jdeploy:registry-smoke -- <version>`.
7. Verify that the README's jDeploy install page remains the public desktop
   install link.

### Trusted Publishing

The npm package should be published through npm Trusted Publishing rather than a
long-lived npm token. The repository workflow is
`.github/workflows/publish-jdeploy.yml`; it is manual-only, requires an explicit
version input that must match `package.json`, runs the desktop/Android gates,
builds the jDeploy npm payload in release mode, and publishes with OIDC when
`mode=publish`.

Configure npm at `npmjs.com` → `@openardf/radio-oracle` → Settings → Trusted
publishing with these exact values:

- Provider: GitHub Actions.
- Organization or user: `OpenARDF`.
- Repository: `Radio-Oracle`.
- Workflow filename: `publish-jdeploy.yml`.
- Environment name: `npm-publish`.
- Allowed actions: `npm publish`.

The workflow uses GitHub-hosted `ubuntu-latest`, Node 24, npm's OIDC
`id-token: write` permission, and `RADIO_ORACLE_RELEASE_BUILD=1` so the desktop
display version matches the package version without an iterative suffix. After
the first successful publish through Trusted Publishing, set the npm package's
Publishing access to require 2FA and disallow traditional tokens.

### GitHub Release Installers

End-user desktop installers are published by
`.github/workflows/jdeploy-github-release.yml`. The workflow runs when a `v*`
tag is pushed, verifies that the tag exactly matches `package.json`, runs the
desktop and Android regression gates, runs the jDeploy release preflight, and
then rewrites the temporary package metadata to the unscoped `radio-oracle`
GitHub-release identity and asks jDeploy to attach GitHub release installer
assets. It also uploads the GitHub jDeploy package tarball to the same GitHub
release for auditability.

For a release version `1.0.5`, the tag must be `v1.0.5`. After the workflow
finishes, users install from:

```text
https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle
```

Current local packaging evidence: the Gradle app-image checks,
`npm run jdeploy:pack-preview`, `npm run jdeploy:release-preflight`,
`npm run jdeploy:local-smoke`, and
`npm run jdeploy:registry-smoke -- 1.0.5` pass on macOS with JDK 17 selected.
Windows packaged-app smoke reached the installed executable and loaded the
sample Race File; the npm helper scripts are cross-platform, but final Windows
confirmation is still tracked through `CODEX_MAILBOX.md`.

### Desktop USB Feasibility

Live SPORTident reader download remains outside the desktop beta boundary, but
the desktop plan now includes a pre-beta USB feasibility check so that SI
download support does not become a late packaging or platform blocker.

The Android reader currently identifies the SPORTident USB bridge by VID/PID
`4292:32778` (`0x10c4:0x800a`) and then talks to it as a serial port. On macOS,
run:

```shell
npm run desktop:usb-diagnostic
```

The diagnostic reports whether that exact SPORTident USB bridge is visible in
macOS IORegistry and lists `/dev/cu.*` USB serial nodes that a future desktop
serial layer could open. Use the stricter form when the SI download box is
expected to be attached:

```shell
npm run desktop:usb-diagnostic -- --require-si
```

Current local evidence on this Mac: `npm run desktop:usb-diagnostic --
--require-si` detects the SPORTident USB bridge as `4292:32778`, product
`SPORTident USB to UART Bridge Controller`, serial `554896`, with
`/dev/cu.SLAB_USBtoUART` available as the macOS serial device node. That removes
the platform-detection showstopper for desktop SI work; the remaining spike is
serial-protocol access and card-readout parity with the Android `SIPort` path.

By default, the desktop app only auto-detects the SPORTident CP2102 USB bridge
identity above. The Time Sync tool also has a persisted
`Probe FTDI/RS232 adapters for SPORTident stations` setting for older BSM7-RS232
stations connected through FTDI USB-to-RS232 adapters. When that setting is
enabled, Radio-Oracle first tries any remembered successful FTDI station port,
then the SPORTident CP2102 bridge, then the other host-visible FTDI serial
ports. A successful FTDI station probe stores that serial node for priority on
future scans. Keep the setting off unless FTDI/RS232 station support is needed,
because a non-responsive FTDI serial node can add probe timeout delay.

### Desktop Automation Hooks

Desktop beta builds include a non-UI automation CLI for repeatable checks and
debug operations. Run it through Gradle with `--args`:

```shell
./gradlew :desktopApp:desktopAutomation --args='version'
./gradlew :desktopApp:desktopAutomation --args='paths'
./gradlew :desktopApp:desktopAutomation --args='logs'
./gradlew :desktopApp:desktopAutomation --args='log-test smoke'
./gradlew :desktopApp:desktopAutomation --args='open-event-file /path/to/Race.json'
./gradlew :desktopApp:desktopAutomation --args='si-status'
./gradlew :desktopApp:desktopAutomation --args='printer-status'
```

The same entrypoint is exposed through npm:

```shell
npm run desktop:automation -- --args='version'
```

Automation commands print one JSON object to stdout for machine parsing. Hardware
status commands are non-failing by default when hardware is absent, which keeps
CI usable. Add `--require` when a local Gradle validation run must fail if no SI
station or printer is available:

```shell
./gradlew :desktopApp:desktopAutomation --args='si-status --require'
./gradlew :desktopApp:desktopAutomation --args='printer-status --require'
```

The CLI is deliberately separate from the Compose UI. It is meant for Codex,
CI, and local diagnostics, not as a hidden remote-control listener in the beta
app.

The desktop serial probe uses jSerialComm in the desktop module only. It
discovers the SPORTident USB VID/PID, opens the selected serial device, sends
the same small SI probe command that Android uses during station setup, reads a
reply, and closes the port:

```shell
npm run desktop:usb-probe
```

To force a specific device node:

```shell
RADIO_ORACLE_SI_PORT=/dev/cu.SLAB_USBtoUART npm run desktop:usb-probe
```

Current local probe evidence: the desktop JVM task opened
`/dev/cu.SLAB_USBtoUART`, selected the SPORTident station serial `554896`, sent
the setup probe at `38400` baud, received the CRC-valid frame
`02 f0 03 00 11 4d 8d 72 03`, then read station system info reporting
`serial=554896 extended=true`.

Single-card desktop readout can be checked with:

```shell
npm run desktop:usb-card-block
```

The continuous-readout feasibility path keeps one station session open and
downloads supported cards as they are inserted:

```shell
npm run desktop:usb-card-loop
```

For unattended smoke tests, cap the loop and shorten the insert timeout:

```shell
RADIO_ORACLE_SI_LOOP_MAX_CARDS=1 RADIO_ORACLE_SI_CARD_EVENT_TIMEOUT_MS=1000 npm run desktop:usb-card-loop
```

Current card-insertion evidence: `npm run desktop:usb-card-event` connected to the
same station at `38400` baud, waited for an SI card action, and detected a card
insert signal with command `0xe8` and SI number `2005010`. That confirms the
desktop serial path can reach the first live readout signal before card block
download.

Current card-download evidence: `npm run desktop:usb-card-block` kept one
serial session open through station setup, card insertion, block download, and
parse. With SI card `2005010` inserted and held in the station, it downloaded
block `0` and block `1`, extracted the 128-byte card blocks from the station's
prefixed `0xef` responses, parsed SI8 series `2`, and reported
`check=10:13:40 start=10:13:43 finish=12:07:10 punches=15`.

Attached download-station comparison is available through:

```shell
npm run desktop:usb-station-diagnostic
```

The diagnostic deduplicates macOS `/dev/cu.*` and `/dev/tty.*` SPORTident
serial nodes by USB serial number, measures probe and long system-info response
timing, prints station serial number, code number, mode code, mode label, and
compares raw system-info offsets against the first detected station. Use
`RADIO_ORACLE_SI_PORTS=/dev/cu.SLAB_USBtoUART,/dev/cu.SLAB_USBtoUART5` to force
an explicit comparison order.

Time-sync readiness can be checked without writing station data:

```shell
npm run desktop:usb-time-sync-inspect
```

To force a specific device node:

```shell
RADIO_ORACLE_SI_PORT=/dev/cu.SLAB_USBtoUART npm run desktop:usb-time-sync-inspect
```

SPORTident time-write support must be validated from captured SI Config+
traffic before Radio-Oracle enables station writes. To inspect copied capture
hex without touching attached hardware, save the captured bytes to a text file
and run:

```shell
npm run desktop:usb-capture-analyze -- --args=/path/to/si-config-capture.txt
```

The analyzer accepts whitespace-separated hex such as `FF 02 F0 ...`, extracts
SPORTident frames, reports command bytes, payloads, frame type, and CRC status.
For station time frames it also decodes the observed `F6`/`F7` time payload.
Use it to compare SI Config+ remote-mode time sync traffic with Radio-Oracle's
known probe and system-info frames before enabling the write command.

The hidden time-sync write command defaults to a no-hardware dry run:

```shell
npm run desktop:usb-time-sync-write -- --args=--time=2026-06-27T03:10:18
```

Dry-run mode prints both the exact captured SI Config+ sequence and the
Radio-Oracle hardware-validation sequence. The validation sequence decodes the
`F6` acknowledgement as the requested station-time echo, sends `F9` to
apply/commit the write, then attempts a post-apply `F7` read before exiting
remote/config mode. It does not open a serial port or write station data.

Actual station writes require explicit opt-in:

```shell
RADIO_ORACLE_SI_TIME_SYNC_WRITE=YES npm run desktop:usb-time-sync-write
```

Optional environment variables:

- `RADIO_ORACLE_SI_PORT=/dev/cu.SLAB_USBtoUART`: force a specific serial node.
- `RADIO_ORACLE_SI_TIME_SYNC_AT=2026-06-27T03:10:18`: write a fixed local time
  instead of the current computer time.
- `RADIO_ORACLE_SI_TIME_SYNC_TOLERANCE_SECONDS=2`: set the allowed difference
  between requested time and the station time echoed by the `F6`
  acknowledgement.
- `RADIO_ORACLE_SI_TIME_SYNC_BOUNDARY_LEAD_MILLIS=225`: when the optional
  second-boundary alignment mode is enabled, target the next whole second and
  begin the final write this many milliseconds before that boundary.
- `RADIO_ORACLE_SI_TIME_SYNC_OFFSET_MILLIS=0`: optional experimental current-time
  target adjustment. Fixed `RADIO_ORACLE_SI_TIME_SYNC_AT` writes are exact and
  do not use this offset.
- `RADIO_ORACLE_SI_TIME_SYNC_ATTEMPTS=2`: retry the full transaction this many
  times when the coupled station fails before `F6` is sent, or when a measured
  post-sync lead-time correction is needed.
- `RADIO_ORACLE_SI_TIME_SYNC_CORRECTION_THRESHOLD_MILLIS=100`: after a
  successful current-time sync, run one correction transaction when the
  post-apply `F7` station-minus-computer delta exceeds this threshold and an
  attempt remains. If the post-apply `F7` read is unavailable, no correction is
  attempted from the `F6` echo alone.
- `RADIO_ORACLE_SI_TIME_SYNC_ALIGN_SECOND=YES`: enable the older calibrated
  pre-boundary whole-second write mode for comparison. By default,
  Radio-Oracle now writes the current computer time directly, including the
  SPORTident 1/256-second tick byte.

Do not run the write-enabled command on race-critical hardware until the
non-critical validation matrix below has passed.

### SPORTident station time-write protocol findings

The current time-sync understanding is based on two SI Config+ USBPcap captures
from June 27, 2026: one setting a coupled SI-Master near 4:50 PM, and one
setting it near 3:10 AM. Treat these findings as capture-proven for the tested
station path. The desktop UI now exposes the Time Sync action after successful
write/acknowledgement validation against expendable hardware, but it still
requires the coupled target station to already be awake.

As of the first successful spare-station test, the coupled target must already
be awake. Manually waking the target station by inserting an SI card, then
coupling it to the SI-Master/download station, allowed the write path to
complete. When the coupled target was asleep, the command stopped before `F6`
with no time write sent. Radio-Oracle prefixes every SPORTident extended command
with the `0xFF` wake byte, including Time Sync commands. If that alone does not
wake a sleeping coupled station, the missing behavior is likely timing or an
additional command sequence that still needs capture evidence.

Radio-Oracle sends the known SPORTident `0xFF` wake signal before station
interactions. Extended sync commands already include the wake byte, and
`Inspect Station` also sends a standalone wake pulse before it reads the remote
or directly attached station settings. If that still does not wake a sleeping
coupled station, the missing behavior is likely timing or an additional command
sequence that still needs capture evidence from SI Config+ or another proven
SPORTident tool waking a sleeping station.

For coupled stations, `Sync Time` now treats the successful baud-detection
remote-mode probe as a wake-prime step. After the probe answers `F0 53`,
Radio-Oracle sends `F0 4D`, closes the port, waits about 250 ms, and only then
opens the actual write transaction. This mirrors the more reliable Inspect
Station wake behavior without sending a clock write until the target station has
answered the normal read-only preflight.

The Time Sync tool exposes `Put station to sleep after sync` as a checkbox that
defaults on. This post-sync power-state write is sent only after the clock write
has succeeded and any retry/correction pass has completed. Direct FTDI/RS232
stations use extended `F8`; coupled remote stations enter remote mode with
`F0 53` and then use the same extended `F8` command through the attached
download station. Direct-station sleep is sent in the same open serial
transaction as the final successful sync. Coupled-station sleep is sent after
the sync transaction exits remote mode and closes, then Radio-Oracle opens a
fresh remote-mode transaction for `F8`; field testing showed the coupled
station did not accept same-session `F8` immediately after the time apply. If
the separate coupled-station sleep command is sent but not confirmed,
Radio-Oracle waits briefly and retries the full remote-mode sleep transaction
once. A failed sleep/off write is reported to the user but does not convert a
completed clock synchronization into a failed clock synchronization. After
every coupled-station sleep transaction, Radio-Oracle also sends `F0 4D` before
closing the port so the attached SI-Master/download station returns to
normal/direct mode for status polling and later inspections.

The tool also has a standalone `Sleep` button next to `Sync Time`. It uses the
same power-state write implementation and retry behavior as the sleep-after-sync
checkbox, but it does not perform any time read or time write first. This is
intended for cases where the operator inspects a station, decides no sync is
needed, and still wants to put the coupled or directly attached station back to
sleep.

The Time Sync panel shares the same SI serial-port mutex used by the status
poller and SI-card readout tools. Inspect and Sync therefore block background
station polling while they are talking to the station, instead of letting the
status strip interleave probe commands with wake, sync, or sleep commands.

The `Inspect Station` action is configuration-read-only. It sends a standalone
`0xFF` wake pulse, keeps the attached USB SI-Master/download-station
diagnostics, and when a coupled station answers remote-mode reads it also
reports the coupled station serial number, SI code number, decoded station time,
computer timestamp, and station-minus-computer delta in milliseconds. It does
not send the optional direct or remote station-sleep command after inspection.
For coupled stations, Inspect now primes the reader with a normal framed
remote-mode transaction (`F0 53`, then `F0 4D` cleanup), waits about 250 ms, and
then performs the remote read. It retries that full prime/read interaction over
a bounded wake window with short 250 ms gaps between attempts. Field testing
showed a sleeping station can take several
seconds to answer after post-sync sleep, and the first interaction may wake the
remote station but return only reader diagnostics while a later interaction
returns the remote station details. If no coupled station answers after that
retry window, Inspect reports `No coupled station found` instead of showing the
download station's own SI code and diagnostics. Direct FTDI/BSF7 inspection
still reports the attached station. The displayed delta uses a millisecond
computer timestamp. The station-time payload tick byte is
interpreted as 1/256 second when station readback provides it, so inspection and
post-sync lead/lag reporting can use subsecond station time. After `Sync Time`,
the UI and hidden CLI report the station-minus-computer delta only when an
actual `F7` station-time read succeeds. The first estimate is attempted
immediately after `F9` while the sync transaction is still open. If that
post-apply read misses, Radio-Oracle opens a separate Inspect-style transaction
and tries one more `F7` read before reporting the lead/lag as unknown. This
matches the local `sportident-python` support scripts' practical measurement
model: sample the computer clock immediately before requesting `F7`, decode the
station's returned tick-aware time, and report station minus computer. Earlier
versions inferred that delta from the `F6` write acknowledgement and could
therefore report a small lead/lag even when the visible applied station clock
appeared roughly one second different.

For current-time syncs, Radio-Oracle now follows the same time-payload model as
the local `sportident-python` support code: it calculates the target station
time as late as possible, immediately before `F6`, and writes the current
computer time directly with the SPORTident tick byte set to the nearest
1/256-second fraction. Earlier builds wrote only whole-second targets with tick
`00` and used a calibrated pre-boundary delay. That older mode remains available
with `RADIO_ORACLE_SI_TIME_SYNC_ALIGN_SECOND=YES` for comparison, but it is no
longer the default.

To improve reliability, Radio-Oracle retries the full sync transaction once
when the failure occurs before the `F6` write command is sent, such as a missed
remote-mode system-info reply. After `F6` has been sent, it does not blindly
retry a failed transaction, because the coupled station may already have
accepted the time write. If a current-time sync succeeds and a post-apply `F7`
read shows the measured station-minus-computer delta is still outside the
correction threshold, Radio-Oracle can use the remaining attempt to subtract
that measured delta from the boundary lead and write again. A missing post-apply
`F7` read now triggers one separate `F7` readback transaction for the displayed
lead/lag estimate. Correction still only uses a measured `F7` delta, never the
`F6` acknowledgement echo.

The captured Config+ remote-mode sequence is:

1. `F0` with payload `53`: probe or enter/configure remote mode.
2. `83` with payload `00 80`: read long system information.
3. `F7` with no payload: read current station time.
4. `F6` with a seven-byte payload: write station time.
5. `F9` with payload `01`: apply/commit the write.
6. `F0` with payload `4D`: return to normal probe/finish state.

All of these are SPORTident extended frames using the existing `0x8005` CRC
implementation in `SportIdentProtocol.buildExtendedMessage`.

Radio-Oracle's validation/write path adds one best-effort `F7` station-time
read after `F9` when possible. That post-apply read is not required to consider
the write acknowledged, but it is required before Radio-Oracle reports a
post-sync station-minus-computer delta or performs a lead-time correction. If
that in-transaction read fails, the service attempts one separate `F7` readback
transaction and uses the same computer-before-`F7` timestamping model as the
Python scripts before falling back to an unknown delta.

Additional SI Config+ serial-port-monitor captures from June 28, 2026 covered a
directly attached BSF7-family station on an FTDI/RS232 adapter at 4800 baud.
Those captures use the same extended frame envelope, `F7` station-time read,
`F6` station-time write, and `F9` apply command, but they do not enter the
inductive remote/coupled path with `F0 53`. The direct BSF7 write sequence is:

1. `F0` with payload `4D`: select/probe normal direct-station communication.
   SI Config+ sent this frame twice before the captured reply, but one
   request/reply is enough for Radio-Oracle's command-client transaction model.
2. `83` with payload `00 80`: read long system information from the directly
   attached station.
3. `F7` with no payload: read current station time.
4. `F6` with a seven-byte payload: write station time.
5. `F9` with payload `01`: apply/commit the write.

As with the coupled path, Radio-Oracle may send a best-effort post-apply `F7`
after `F9` so the reported lead/lag is based on a real station-time read rather
than the `F6` acknowledgement echo. If the immediate read misses, it may reopen
the direct FTDI station path and perform the same one-shot `F7` fallback used
for coupled stations.

Radio-Oracle therefore treats opted-in FTDI/RS232 SPORTident ports as direct
attached stations for Time Sync. The existing SPORTident USB CP2102 SI-Master
path continues to use the remote/coupled sequence above. The BSF7 direct long
system-information reply also places the visible SI station code at data offset
17 in the captured station-32 frame; offset 1 was `06` and is not the station
code for that layout.

The local GPL-3.0 `sportident-python` repository was used as a protocol
reference, not as source to copy. It confirms the extended-command meanings
already seen in captures (`F0 4D` direct/master, `F0 53` indirect/remote,
`F6` set time, `F7` get time, `F8` off, `F9` beep/apply in the observed time
write flow, `FE` baud select), the `0xFF` wake byte convention, and SYS_VAL
offsets for read-only station diagnostics. Radio-Oracle already prefixes
extended commands with `0xFF`; Inspect Station now parses optional firmware,
model id/name, build date, battery date, memory size, voltage, active time, and
protocol-byte diagnostics from long system-info replies when those fields are
present.

The same reference identifies extended `F8` as the station-off command for the
station currently being addressed. In remote mode, Radio-Oracle now sends this
normal framed `F8` command through the attached download station. A special raw
remote-off byte sequence was also captured and noted, but field testing showed
that using it as the post-sync sleep action did not turn off the coupled
station:

```text
FF 40 0F 80 B2 B6 50 C0
```

The station-time payload is not BCD. The observed seven-byte payload is:

```text
YY MM DD DAY_HALF SECONDS_HI SECONDS_LO TICK
```

The fields are:

- `YY`: binary two-digit year, so `1A` means 2026.
- `MM`: binary month, one-based.
- `DD`: binary day of month.
- `DAY_HALF`: `(siDayOfWeek << 1) | halfDayFlag`.
- `SECONDS_HI SECONDS_LO`: big-endian seconds within the current 12-hour
  half-day.
- `TICK`: subsecond/tick byte. Config+ writes `00`; station replies may return a
  non-zero tick. Treat it as fractional seconds in 1/256-second units. Current
  Radio-Oracle sync writes also use this tick byte, matching the Python support
  library's `datetime.microsecond / 1_000_000 * 256` approach with rounding.

The SI day-of-week mapping follows the existing card-punch parser convention:
Sunday is `0`, Monday is `1`, through Saturday as `6`. `halfDayFlag` is `0` for
AM and `1` for PM.

Captured examples:

```text
2026-06-27 16:50:12
F6 payload: 1A 06 1B 0D 44 04 00
YY=1A, MM=06, DD=1B, DAY_HALF=0D -> siDay=6 and PM,
SECONDS=4404 -> 17412 seconds -> 04:50:12 within PM half-day, TICK=00

2026-06-27 03:10:18
F6 payload: 1A 06 1B 0C 2C 9A 00
YY=1A, MM=06, DD=1B, DAY_HALF=0C -> siDay=6 and AM,
SECONDS=2C9A -> 11418 seconds -> 03:10:18 within AM half-day, TICK=00
```

The existing frame builder reproduces the captured SI Config+ write frames
exactly:

```text
FF 02 F6 07 1A 06 1B 0D 44 04 00 10 91 03
FF 02 F6 07 1A 06 1B 0C 2C 9A 00 63 C7 03
```

The validation matrix used before enabling the `Time Sync` button covered
non-critical hardware with the hardware-gated command:

1. Non-critical ordinary station, AM time.
2. Non-critical ordinary station, PM time.
3. Non-critical ordinary station, near noon.
4. Non-critical ordinary station, near midnight.
5. Master station, AM time.
6. Master station, PM time.
7. Master station, near noon.
8. Master station, near midnight.

For each run, require the command to read the station time before writing, write
`F6`, decode the `F6` acknowledgement with `DesktopSportIdentStationTimeCodec`,
fail before `F9` if the echoed station time is outside the configured tolerance,
then apply the write with `F9`. Current builds then attempt a best-effort
post-apply `F7` read to measure the applied station clock. If that post-apply
read is unavailable, the write can still be successful, and Radio-Oracle opens
one separate readback transaction to estimate the post-sync lead/lag from `F7`.
If that fallback also fails, the post-sync lead/lag is reported as unknown and
correction is not run from the `F6` echo alone. The first successful
spare-station run wrote requested time
`2026-06-27T17:52:02`, decoded `F6` confirmation `2026-06-27T17:52:02`, then
applied the write.

Manual-wake spare-station validation on coupled target station `575853` passed
these fixed-time cases with a three-second tolerance:

- AM: requested and confirmed `2026-06-27T03:10:18`.
- PM: requested and confirmed `2026-06-27T16:50:12`.
- Near noon: requested and confirmed `2026-06-27T11:59:50`.
- Near midnight: requested and confirmed `2026-06-27T23:59:50`.

After the matrix, the station was restored to the computer's current time:
requested `2026-06-27T19:02:40`, confirmed `2026-06-27T19:02:40`.

Manual-wake Master-station validation on coupled target station `559846` passed
the same fixed-time cases with a three-second tolerance:

- AM: requested and confirmed `2026-06-27T03:10:18`.
- PM: requested and confirmed `2026-06-27T16:50:12`.
- Near noon: requested and confirmed `2026-06-27T11:59:50`.
- Near midnight: requested and confirmed `2026-06-27T23:59:50`.

After the matrix, the station was restored to the computer's current time:
requested `2026-06-27T19:08:45`, confirmed `2026-06-27T19:08:45`.

For local macOS smoke tests, prefer copying the generated `.app` and sample
Race File to `/tmp` before launching with `open ... --args <sample.rom.json>`.
Launching the checkout-built app bundle directly from `Documents/GitHub` can
trigger a macOS Documents-folder permission prompt, which is not useful for
repeatable package validation.

## First Implementation Slices

1. Done: add golden-file coverage for the existing full race export shape.
2. Done: add a desktop app module with a minimal launch window and no race editing.
   The shell uses Compose Desktop, Android-derived colors, Android navigation
   vocabulary, and a non-editing status strip.
3. In progress: add file-backed open/save for a shared Race File envelope.
   The shared `.rom.json` envelope now has a tested JSON codec; desktop file
   filesystem wiring and current Race File session state now live in the desktop
   app module. File menu open/save/export-copy/close wiring is present, dirty
   open/close/exit paths prompt to save, discard, or cancel, and the app can
   create a new empty Race File using shared Android-compatible race defaults or
   accept a startup `.rom.json` path for repeatable smoke runs.
4. In progress: add the first race-admin screen backed by shared models and
   services. The Races section now shows race details from shared display models
   and can edit the race name, start date/time, race type, race level, race
   band, and time limit through shared Race File editing rules. Desktop
   Race File session state now tracks unsaved edits and can save back to the
   current Race File path. The
   Categories section now shows category rows using shared effective race settings, can add categories with conservative
   defaults, and can edit category names, length, climb, and control-point
   strings through shared Race File editing rules. Dense category and competitor
   grids use fixed-width columns with horizontal scrolling. Category,
   competitor, alias, readout, and result rows use one-line row action buttons;
   add actions stay reachable beside scrollable entry fields where the entry
   row can run wider than the window. Delete actions sit in the same fixed left
   action column and require confirmation before removal. Add-row drafts remain
   in place when shared validation rejects the edit.
   The Competitors section now shows competitor rows with shared category lookup
   and display formatting, can add uncategorized competitors with conservative
   defaults, and can edit competitor first and last names, category assignment,
   club, index, birth year, start numbers, SI numbers, and competitor deletion
   through shared Race File editing rules. Deleted competitor readouts can be kept
   as unmatched readouts or deleted with the competitor, matching the Android
   deletion policy.
   The Aliases section can
   add/delete aliases and edit existing alias SI codes and names through shared
   alias validation rules. The Readouts section now shows matched and unmatched
   SI-card readout rows and can delete readouts through shared Race File editing
   rules, set an explicit manual result status, or create manual readouts with
   competitor matching, SI number, start/finish seconds, and control punch
   codes. The Start List section can draw start times by category with a
   configurable interval and club rotation. The Results section now shows
   competitor result rows and can set the same explicit manual result status for
   matched readouts. The Settings
   section now shows Race File diagnostics, shared race validation issues, and
   the desktop beta scope boundary. The File menu can import Android-compatible
   category, competitor, ARDFEvent registration, start-list CSV, and
   race-backup JSON files; export
   categories, competitors, starts, starts-by-category, starts-by-minute, readouts, and
   results as semicolon-delimited CSV files using shared formatters; export
   ROBIS start-list CSV, ARDFEvent-style results CSV, result TXT/HTML, IOF
   start/result-list XML, Android-shaped race-backup, live-result, and
   final-result JSON, and standards-facing ARDF JSON. A sample
   smoke-test Race File is available at `samples/desktop-smoke.rom.json`, with
   automated desktop coverage for the session-level open, edit, save, close,
   reopen, export-copy, CSV export, and ARDF JSON export flows.
5. In progress: keep the jDeploy deployment path aligned with the desktop smoke
   scenario. The Gradle-side jDeploy bundle tasks now build and verify
   `desktopApp/build/jdeploy/Radio-Oracle-jdeploy.jar`. The npm/jDeploy
   package metadata now uses `@openardf/radio-oracle`, local package preview is
   covered by `npm run jdeploy:pack-preview`, local install/launch smoke is
   covered by `npm run jdeploy:local-smoke`, public install/launch smoke is
   covered by `npm run jdeploy:registry-smoke -- <version>`, GitHub release
   installer assets are published by `.github/workflows/jdeploy-github-release.yml`,
   and npm registry/provenance publishing is handled by
   `.github/workflows/publish-jdeploy.yml`.
6. In progress: keep hardened Android-style race-day SI download post-beta, but
   continue desktop USB feasibility, single-card download, and experimental
   continuous-readout slices before relying on that deferral. The first
   diagnostic command is
   `npm run desktop:usb-diagnostic`; it confirms macOS USB serial visibility
   and can require the known SPORTident VID/PID when the download box is
   attached. The second command is `npm run desktop:usb-probe`; it opens the
   serial device, sends the setup probe, reads the station response, and closes
   the port. `npm run desktop:usb-station-diagnostic` compares attached
   download stations for response timing and system-info differences. The
   desktop Readouts screen now has a single-shot "Download SI" action and
   experimental continuous "Start SI" / "Stop SI" controls that add
   SI5/SI6/SI8/SI9/SIAC card readouts to the open Race File when the attached
   station is present and in READOUT/SI MASTER mode.
7. Long-term: add Station Maintenance after the desktop race-admin beta is
   stable. The first version should be read-only for the attached USB
   master/download station and should report serial number, station mode/code,
   protocol flags, firmware/config metadata when available, and clear warnings
   when a download box is not in READOUT/SI MASTER mode. A later read-only phase can read
   station information for non-reader SI stations coupled magnetically to the
   USB master, including battery/status fields and operating-time/status fields
   if the remote/coupled-station protocol exposes them. Station write actions,
   including "set this download box to READOUT", should remain disabled until
   the SPORTident configuration-write transaction is verified on real hardware
   and protected by confirmation plus immediate read-back validation.
