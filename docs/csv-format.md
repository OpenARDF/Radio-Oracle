# Radio-Oracle CSV format

Standard CSV exports on desktop and Android use UTF-8, comma-separated columns,
and a header row, including when there are no data rows. Fields containing commas,
double quotes, CR, or LF are enclosed in double quotes; embedded double quotes are
doubled. Quoted fields may span physical lines. Readers accept LF, CRLF, CR, and an
optional UTF-8 BOM. Blank lines are ignored; empty cells and trailing cells are retained.

This applies to categories, controls, competitors, starts (including alternate sort
orders), readouts, results, split results, course reports, and downloadable split
results on public-results sites. Existing column meanings and units are retained.
Course reports already used commas; they now share the common quoting implementation.

## Categories

```csv
category,is_man,max_age,length_m,climb_m,follows_race_presets,race_type,time_limit_min,race_band,control_count,controls
M21,1,99,5155,174,1,,,,6,"71,72,73,74,75,79B"
W65,0,99,0,0,1,,,,5,"71,72,73,74,79B"
```

`is_man` is `1` for Men and `0` for Women (the existing category flag).
The competitor CSV's existing `gender` column instead uses `0` for Men and `1` for
Women. Length and climb are metres; zero length is allowed for categories whose
course metrics have not yet been assigned. Control lists stay in one quoted cell.
An empty control set has `control_count=0` and an empty `controls` cell.

The optional `encrypted_ideal_order` column is still included only when requested
by the existing protected-data export workflow. CSV cleanup does not expose route
secrets or change course-data protection.

## Other exports

- Competitors and starts: see [Competitor CSV](competitor-csv.md).
- Controls: `si_code,role,fox,public_label,notes`; notes can contain quoted newlines.
- Readouts: `si_number,check_time,start_time,finish_time,control_count`, followed by
  `control_1_code,control_1_time`, etc. The widest readout determines the header;
  shorter readouts are padded with empty cells.
- Results: a header is always present. Optional award and route-analysis columns
  have corresponding empty cells for rows without that information.
- Split results and course reports retain their existing headings and units.

## Import compatibility

Category, competitor, control, and start-list imports accept both comma-separated
CSV and existing semicolon files, with optional recognized headers. The delimiter
is selected for the whole file, using the header when available and validated rows
otherwise. Commas in a legacy control list do not become extra columns. Malformed
records report the physical starting line of the record. Do not mix delimiters
between the header and data rows.

Existing ARDFEvent registration inputs remain accepted. The EventReg spreadsheet
reader uses the same quoted-record parser for comma-separated downloads.

## Dedicated external compatibility exports

Two explicitly named formats retain their existing layouts and semicolon dialect:
**Export ROBIS Start List CSV** (headerless) and **Export ARDFEvent Results CSV**
(with its existing header). They use the shared escaping implementation with their
own delimiter. These are compatibility profiles, not the standard Radio-Oracle
CSV format. Neither external integration's wire format is silently changed.
