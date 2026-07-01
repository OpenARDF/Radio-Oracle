# Hidden Debug Logging

Radio-Oracle writes a small diagnostic log under app-private storage. The logs
are intended for developer troubleshooting and are not part of normal Race File
data.

## Location

Android stores logs in:

```shell
/data/data/org.openardf.radiooracle/files/debug-logs/
```

Desktop stores logs in the operating system's app-data area:

```shell
macOS:   ~/Library/Application Support/Radio-Oracle/logs/
Windows: %APPDATA%\Radio-Oracle\logs\
Linux:   ${XDG_STATE_HOME:-~/.local/state}/Radio-Oracle/logs/
```

The active file is `debug.log`. Older files are retained as `debug.log.1` and
`debug.log.2` according to the rolling-log retention policy. Android and desktop
currently use a 512 KB active file and retain three files total.

## Current Scope

Android records low-volume breadcrumbs for:

- app startup;
- USB-device scanning and attach intents;
- SI reader service start/stop;
- SI station probe/connect results;
- card insert and card-read outcomes.

Desktop records low-volume breadcrumbs for:

- app startup and log initialization;
- Race File create/open/save/save-as/close/export-copy results;
- SI station status changes;
- single and continuous SI readout start/stop, timeout, failure, and card
  download outcomes.

The log should not contain raw live-result payloads, API keys, full imported
files, competitor names, or other broad personal race data.

## Developer Extraction

For debug builds, use `adb run-as`:

```shell
adb shell run-as org.openardf.radiooracle ls -l files/debug-logs
adb exec-out run-as org.openardf.radiooracle cat files/debug-logs/debug.log
```

To reset the hidden debug logs during a test:

```shell
adb shell run-as org.openardf.radiooracle rm -rf files/debug-logs
```

On desktop, use `Settings/Help > Logs` in the workflow navigation to display
the current log directory in the status area.
