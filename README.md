# Radio-Oracle

Radio-Oracle is an Android app for managing radio orienteering events on race
day.

Radio-Oracle is maintained by OpenARDF and derived from the MIT-licensed
Radio-O-Manager project by Pavel Kolský and contributors.

## What It Does

Radio-Oracle is intended to help organizers:

1. create or import races with categories, aliases, and competitors
2. read SportIdent cards
3. calculate and review results
4. export results
5. print finish tickets
6. send live results

## Desktop Beta

Radio-Oracle also has a desktop event-admin beta distributed through jDeploy as
`@openardf/radio-oracle`. The desktop app is intended for pre-event and
post-event administration away from the finish table.

The desktop beta supports:

- opening, editing, saving, and exporting `.rom.json` project files;
- managing races, categories, control points, aliases, competitors, manual
  readouts, and results;
- importing Android-compatible category, competitor, and start-list CSV files;
- exporting category, competitor, start-list, start-list-by-category,
  start-list-by-minute, readout, result, result TXT/HTML, IOF start/result-list
  XML, and ARDF JSON files;
- detecting an attached SPORTident USB download box and warning when it is not
  in READOUT/SI MASTER mode;
- downloading one SI5/SI6/SI8/SI9/SIAC card at a time from an attached
  READOUT/SI MASTER station;
- running an experimental continuous SI5/SI6/SI8/SI9/SIAC card readout loop
  from an attached READOUT/SI MASTER station;
- choosing whether duplicate SI-card reads are ignored, replaced, or stored as
  new readouts;
- showing the most recent readout SI card, competitor, status, timestamp, and
  warning/error state;
- playing optional desktop alert sounds for duplicate or error/unknown SI
  readouts;
- inspecting competitors in drawn start-time order in a Start List desktop view;
- drawing start times by category with club rotation and a configurable interval;
- assigning unmatched readouts to competitors;
- marking competitors DNS without an SI-card readout;
- previewing finish-ticket text for readouts using the shared ticket renderer;
- summarizing live-result send readiness and exporting Android-shaped
  live-result JSON payloads;
- manually sending unsent matched live results to ROBIS using the race API key;
- sending unsent matched ROBIS live results in the background when enabled in
  Settings;
- serving a loopback-only local result display plus `/results.json` and
  `/categories.json` endpoints from the open desktop project;
- tracking started competitors without readouts in an In Forest desktop view.

The desktop beta does not yet replace the Android race-day workflow. Live
printer transport, shared SQL persistence, and station maintenance writes remain
post-beta work.

Local desktop packaging and smoke commands are documented in
[`docs/desktop-prep.md`](docs/desktop-prep.md).

## Equipment Needed

- SportIdent BSM 7 / BSM 8 reader
- USB to OTG adapter
- Bluetooth printer for ticket printout

## Supported Competition Formats

- Classics
- Foxoring
- Orienteering
- Sprint

## Third Party Libraries and Resources

- **Logo** - original Radio-Oracle artwork generated for this project using Codex
- [SortableTableView](https://github.com/ISchwarz23/SortableTableView)
- [kotlin-csv](https://github.com/doyaaaaaken/kotlin-csv)
- [UsbSerial](https://github.com/felHR85/UsbSerial)
- [ESCPOS-ThermalPrinter-Android](https://github.com/DantSu/ESCPOS-ThermalPrinter-Android)
- [Moshi](https://github.com/square/moshi)
- [OkHttp](https://github.com/square/okhttp)
- [Markwon](https://github.com/noties/Markwon)

## Credits

- OpenARDF maintains Radio-Oracle.
- Radio-Oracle is derived from the MIT-licensed Radio-O-Manager project by
  Pavel Kolský, Vojtěch Kopal, and Jakub Šrom.
