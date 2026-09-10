These fixtures reproduce the structure of Condes 10.8.17 GPX and KMZ course
exports from a September 2026 import failure. Locations are invented, venue
names are replaced, and map/image overlays are removed. The test wraps the KML
in a KMZ archive with a preceding non-KML entry.

The regression covers course sequence labels such as `1 (71)`, GPX point types,
and KML course-setting ExtendedData. Sequence numbers are route order, not fox
identities; the parenthesized/explicit control code supplies the SI hint.

For local verification against the original report, set
`RADIO_ORACLE_CONDES_REPORT_DIRECTORY` to the folder containing its original
GPX and KMZ attachments when running `just test`. The optional attachment test
is skipped when that environment variable is absent.
