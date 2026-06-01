# Desktop Preparation Notes

These notes define the first desktop target before a user-visible desktop app is
added. They keep the desktop effort focused on a small event-admin beta instead
of full Android parity.

## Desktop Beta Boundary

The first desktop app should be a thin UI over shared event-domain services. It
should support event administration workflows that are useful away from the
finish table:

- create, open, edit, save, and export event files;
- manage races, categories, control points, aliases, competitors, readouts, and
  results;
- manually enter or edit readout-equivalent punch data;
- recalculate results using shared services;
- import/export supported event and result formats as they move into shared
  code.

The desktop beta should not include:

- live SPORTident reader download;
- Bluetooth or ticket printing;
- live result sending;
- Android Room database migration or shared SQL persistence;
- any promise that desktop can replace the Android race-day download workflow.

## Storage

Use file-backed project storage for the beta, most likely a `.rom.json` project
file. Shared SQL remains post-beta. A later SQL spike should compare Room KMP as
the baseline candidate against SQLDelight as the fallback/comparison option.

## UI And Module Shape

Prefer a small desktop app module that depends on `:shared`. Compose
Multiplatform Desktop is the default UI candidate because it keeps the app in
Kotlin and aligns naturally with the existing Kotlin Multiplatform foundation.

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

The desktop app should keep platform concerns thin:

- file pickers and local filesystem permissions in the desktop module;
- event validation, formatting, placement, and import/export policy in shared
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
  sample event.

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
17 runtime when available and runs the local app-image packaging smoke.
`scripts/jdeploy-prepare.sh` does the same for the two Gradle-side jDeploy
bundle tasks.

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
metadata will consume.

The selected public jDeploy/npm package identity is `@openardf/radio-oracle`.
Local metadata checks are available through:

```shell
npm install
npm run jdeploy:pack-preview
npm run jdeploy:local-smoke
npm run jdeploy:release-preflight
npm run jdeploy:registry-smoke -- 1.0.1
```

`npm run jdeploy:pack-preview` prepares the Gradle-side jDeploy bundle, runs
`jdeploy package`, and shows the npm tarball payload without publishing. A real
publish is guarded by `scripts/check-jdeploy-publish.mjs` and requires
`RADIO_ORACLE_ALLOW_JDEPLOY_PUBLISH=1`.

`npm run jdeploy:local-smoke` installs the package locally, verifies the
generated app bundle, launches it with a `/tmp` copy of
`samples/desktop-smoke.rom.json`, confirms the process starts, and quits the
app.

`npm run jdeploy:release-preflight` checks package identity and version
alignment before any intentional publish.

After a public publish, `npm run jdeploy:registry-smoke -- <version>` installs
that exact registry version in a temporary directory, launches it with the smoke
project, confirms startup, and quits the app.

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
the setup probe at `38400` baud, and received `02 f0 03 00 11 4d 8d 72 03`.

For local macOS smoke tests, prefer copying the generated `.app` and sample
project file to `/tmp` before launching with `open ... --args <sample.rom.json>`.
Launching the checkout-built app bundle directly from `Documents/GitHub` can
trigger a macOS Documents-folder permission prompt, which is not useful for
repeatable package validation.

## First Implementation Slices

1. Done: add golden-file coverage for the existing full race export shape.
2. Done: add a desktop app module with a minimal launch window and no event editing.
   The shell uses Compose Desktop, Android-derived colors, Android navigation
   vocabulary, and a non-editing status strip.
3. In progress: add file-backed open/save for a shared project envelope.
   The shared `.rom.json` envelope now has a tested JSON codec; desktop file
   filesystem wiring and current-project session state now live in the desktop
   app module. File menu open/save/export-copy/close wiring is present, dirty
   open/close/exit paths prompt to save, discard, or cancel, and the app can
   create a new empty project using shared Android-compatible race defaults or
   accept a startup `.rom.json` path for repeatable smoke runs.
4. In progress: add the first event-admin screen backed by shared models and
   services. The Races section now shows race details from shared display models
   and can edit the race name, start date/time, race type, race level, race
   band, and time limit through shared project-editing rules. Desktop
   project-session state now tracks unsaved edits and can save back to the
   current project path. The
   Categories section now shows category rows using shared effective race settings, can add categories with conservative
   defaults, and can edit category names, length, climb, and control-point
   strings through shared project-editing rules.
   The Competitors section now shows competitor rows with shared category lookup
   and display formatting, can add uncategorized competitors with conservative
   defaults, and can edit competitor first and last names, category assignment,
   club, index, birth year, start numbers, SI numbers, and competitor deletion
   through shared project-editing rules. Deleted competitor readouts can be kept
   as unmatched readouts or deleted with the competitor, matching the Android
   deletion policy.
   The Aliases section can
   add/delete aliases and edit existing alias SI codes and names through shared
   alias validation rules. The Readouts section now shows matched and unmatched
   SI-card readout rows and can delete readouts through shared project-editing
   rules, set an explicit manual result status, or create manual readouts with
   competitor matching, SI number, start/finish seconds, and control punch
   codes. The Results section now shows competitor result rows and can set the
   same explicit manual result status for matched readouts. The Settings
   section now shows project diagnostics, shared event validation issues, and
   the desktop beta scope boundary. The File menu can export categories,
   competitors, starts, readouts, and results as semicolon-delimited CSV files
   using shared formatters. A sample smoke-test project is available at
   `samples/desktop-smoke.rom.json`, with automated desktop coverage for the
   session-level open, edit, save, close, reopen, export-copy, and CSV export
   flows.
5. In progress: add jDeploy metadata after the desktop app can complete a real
   smoke scenario. The Gradle-side jDeploy bundle tasks now build and verify
   `desktopApp/build/jdeploy/Radio-Oracle-jdeploy.jar`. The npm/jDeploy
   package metadata now uses `@openardf/radio-oracle`, local package preview is
   covered by `npm run jdeploy:pack-preview`, local install/launch smoke is
   covered by `npm run jdeploy:local-smoke`, public install/launch smoke is
   covered by `npm run jdeploy:registry-smoke -- <version>`, and the first
   public npm package is published as `@openardf/radio-oracle@1.0.1`.
6. In progress: keep live SI download post-beta, but run a desktop USB
   feasibility spike before relying on that deferral. The first diagnostic
   command is `npm run desktop:usb-diagnostic`; it confirms macOS USB serial
   visibility and can require the known SPORTident VID/PID when the download
   box is attached. The second command is `npm run desktop:usb-probe`; it opens
   the serial device, sends the setup probe, reads the station response, and
   closes the port.
