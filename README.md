# Radio-Oracle

Radio-Oracle is an app for managing radio orienteering races, including race
setup, SPORTident readout, results, exports, and live-result support.

Download the latest desktop release:
[Install Radio-Oracle](https://www.jdeploy.com/gh/OpenARDF/Radio-Oracle)

License: MIT. See [LICENSE](LICENSE).

## What It Does

Radio-Oracle is intended to help organizers:

1. create or import races, categories, controls, and competitors
2. read SPORTident cards
3. calculate and review results
4. export race data and results
5. print finish tickets
6. send live results

The goal is to make radio orienteering race administration practical before,
during, and after race day.

## Platform Status

- Android is the established race-day app.
- The desktop app is a beta for race setup, administration, analysis, readout,
  and results work on Windows and macOS.
- Linux desktop support is best-effort and not yet a primary validation target.

## Project Status

The project currently includes:

- Android race-day workflows for SPORTident readout, results, printing, and live
  results
- a desktop Race File editor for `.rom.json` race data
- desktop import/export tools for categories, competitors, starts, readouts,
  results, Android-compatible JSON, IOF XML, ARDF JSON, KML/KMZ controls, and
  course analysis artifacts
- desktop SPORTident download support for attached READOUT/SI MASTER stations
- shared Kotlin race, result, import/export, and validation logic

## More Information

- [Desktop preparation and packaging](docs/desktop-prep.md)
- [Course Analyzer documentation](docs/course-analyzer.md)
- [Competitor CSV format](docs/competitor-csv.md)

## Credits

Radio-Oracle is maintained by OpenARDF and derived from the MIT-licensed
Radio-O-Manager app by Pavel Kolský, Vojtěch Kopal, and Jakub Šrom.
