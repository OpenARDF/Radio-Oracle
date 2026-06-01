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
- importing Android-compatible category, competitor, start-list, readout, and
  result CSV files;
- exporting category, competitor, start-list, readout, result, and ARDF JSON
  files;
- detecting an attached SPORTident USB download box and warning when it is not
  in READOUT/SI MASTER mode;
- downloading one SI6/SI8/SI9/SIAC card at a time from an attached
  READOUT/SI MASTER station.

The desktop beta does not yet replace the Android race-day workflow. Live
continuous SPORTident card download, ticket printing, live result sending,
shared SQL persistence, and station maintenance writes remain post-beta work.

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
