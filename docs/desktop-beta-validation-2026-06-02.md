# Desktop Beta Validation - 2026-06-02

Branch: `codex/multiplatform-beta-work`

## Local Build And Packaging

Validated on macOS before hardware checks:

- `./gradlew :shared:check testDebugUnitTest :shared:desktopSmokeRun :desktopApp:test`
- `git diff --check`
- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :desktopApp:checkRuntime :desktopApp:createDistributable :desktopApp:verifyDesktopDistributable :desktopApp:prepareDesktopJdeployBundle :desktopApp:verifyDesktopJdeployBundle`
- `npm install`
- `npm run jdeploy:pack-preview`
- `npm run jdeploy:local-smoke`
- `npm run jdeploy:release-preflight`

All passed.

## macOS Hardware

Printer:

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :desktopApp:desktopPrinterProbe`
- Detected system printers: `DYMO LabelManager 280`, `EPSON ET-2720 Series`.
- `RADIO_ORACLE_PRINTER="EPSON ET-2720 Series" RADIO_ORACLE_PRINT_TEST=1 JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew :desktopApp:desktopPrinterProbe`
- Probe reported: `Printed finish ticket to EPSON ET-2720 Series.`
- User confirmed the print worked.

SPORTident station:

- `npm run desktop:usb-diagnostic -- --require-si`
- SPORTident USB bridge detected in macOS IORegistry.
- Serial path: `/dev/cu.SLAB_USBtoUART`.
- USB serial: `554896`.

Station probe:

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) npm run desktop:usb-probe`
- Station serial: `554896`.
- Baud: `38400`.
- Code number: `15`.
- Mode: `SI MASTER`.

Station diagnostic:

- `JAVA_HOME=$(/usr/libexec/java_home -v 17) npm run desktop:usb-station-diagnostic`
- Probe timing: min `7.3ms`, median `21.7ms`, max `44.8ms`.
- System-info timing: min `162.7ms`, median `163.2ms`, max `164.5ms`.

Card-loop read:

- `RADIO_ORACLE_SI_LOOP_MAX_CARDS=1 JAVA_HOME=$(/usr/libexec/java_home -v 17) npm run desktop:usb-card-loop`
- Read SI card `2005010`.
- Card type: `0xe8`.
- Downloaded blocks: `0`, `1`.
- ACK sent.
- Parsed series: `2`.
- Parsed punches: `6`.
- Parsed finish: `12:08:32`.
- Probe finished with `cards read=1`.

## Windows

Windows 11 packaged-app smoke validation is pending. The handoff request is in
`CODEX_MAILBOX.md`; Windows Codex should reply there, commit, and push.

