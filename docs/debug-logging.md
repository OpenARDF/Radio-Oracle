# Diagnostic log files

Radio-Oracle captures logs automatically. Users do not need to leave a log viewer
open or enable a diagnostic mode before encountering a problem.

## Sending logs

- **Android:** top-level Settings > Support > Diagnostic Logs > Share Logs.
  Choose a mail or messaging app and the support recipient. **Save ZIP** provides
  Android's file picker instead, for saving and attaching the file later.
- **Desktop:** Setup > More... > Send Diagnostic Logs... > Export ZIP. Attach the
  saved ZIP to a support message. Open Log Folder also provides direct file access.

Include what happened, the approximate time and time zone, the SI card number,
and the station/card model if known. Export soon after a problem: retention is
bounded and older logs rotate out. There is no in-app live log viewer or automatic
submission, and no support address is hard-coded.

This follows SerialSlinger's Android share-sheet/attachment approach and desktop
log-folder access, with one ZIP snapshot of all retained Radio-Oracle logs so
users do not need to select the right rotated file. Snapshotting is synchronized
with file rotation. Sharing does not stop the reader or share an actively changing
file. The diagnostic ZIP also contains a README with app/build/platform details.

The ZIP can contain SI numbers, punch times, cardholder data encoded on SI cards,
and device/file details. The UI discloses this before sharing. Only explicitly
named diagnostic files are included; event databases, race files, preferences,
API keys and other settings are not collected by the exporter.

## Capture and retention

Both platforms keep operational breadcrumbs in `debug.log`, `debug.log.1`, and
`debug.log.2`, with 512 KiB per file. These cover app startup, station connections,
USB devices, read/store outcomes, and existing import/export/service activity.

Detailed card-download traffic goes to a separate `sportident.log` and archives
`.1` through `.3`, with 2 MiB per file (8 MiB total). This keeps packet traffic
from displacing the lower-volume operational history. It includes:

- app version, build date, platform, diagnostic format and parser revision marker;
- unique read IDs, SI number/card family, Android event/station context or desktop port;
- transmitted packet bytes and actual write counts;
- requested command/block, expected response size, timeout and retry policy;
- raw received chunks, original chunk sizes, read duration and monotonic timestamps;
- assembled frame command/length/CRC outcome, unsolicited frame caching,
  remaining partial data at timeout, and buffer trimming;
- per-attempt outcome, retry delay, final parsing outcome and exceptions.

Chunk traces are collected in memory and flushed after each card command, including
failed commands and exceptions. The UTC timestamp on a flushed line is the write
time; use the `CLOCK monotonicMs` anchor, `+Nms` offsets, and the read-begin UTC and
monotonic anchor to reconstruct when I/O actually occurred. Transmit lines have
their own UTC write timestamps. Individual command traces are bounded at 64 KiB;
chunks above 4096 bytes report truncation. END reports empty reads, omitted events,
and the last outcome even if the capture filled. A process kill before a command
finishes may lose that command's in-memory chunks. Logging failures must not change
a download result. Raw data permits replay of unexpected framing/CRC/parser cases;
a future physical test with Ruth's card remains necessary.

Android share snapshots are placed only in `cache/diagnostic-exports/`, exposed
through a non-exported FileProvider with temporary read grants. Snapshots older
than seven days are removed when the next export is made. Save ZIP writes through
the system document picker. Desktop writes a temporary ZIP then replaces the
selected output only after ZIP creation succeeds.

## Developer locations

Android: `/data/data/org.openardf.radiooracle/files/debug-logs/`.

Desktop:

```text
macOS:   ~/Library/Application Support/Radio-Oracle/logs/
Windows: %APPDATA%\Radio-Oracle\logs\
Linux:   ${XDG_STATE_HOME:-~/.local/state}/Radio-Oracle/logs/
```

For Android debug builds, inspect using `adb shell run-as org.openardf.radiooracle
ls -l files/debug-logs`. Operational logging should continue to exclude raw
network/result payloads, credentials, full imported files, and broad race data.
The raw SPORTident trace is the narrowly scoped exception for card diagnostics.
