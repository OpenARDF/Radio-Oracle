# Race File Data Model Notes

## Race Settings

The Race File race record is the source of truth for race type, band, and time limit.
Categories inherit those values. Mixing Classic, Sprint, Foxoring, or other race types inside one
race is not supported; use a Race Series when different formats or materially different race
settings need to be administered together.

Older Android databases, CSV files, and JSON Race Files may contain category-level race setting
fields such as `differentProperties`, `raceType`, `raceBand`, or category time limit. Those fields
are legacy compatibility data only. Current Radio-Oracle behavior:

- reads old files without failing,
- ignores category-level race setting values when scoring, validating, importing, displaying, and exporting,
- validates old category-level race settings as a warning,
- clears those legacy values when writing current Race Files and exports.

Category length, climb, assigned controls, protected route/course data, competitors, and results
remain category-specific where applicable.
