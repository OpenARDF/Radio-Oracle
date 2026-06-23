# Competitor CSV

Radio-Oracle imports and exports competitor registration data as semicolon-delimited CSV.

The canonical header is:

```text
si_number;start_number;first_name;last_name;category;gender;birth_year;club;index;start_time;si_rent;preferred_start_group;bib_number;call_sign
```

Columns:

- `si_number`: Optional SportIdent card number.
- `start_number`: Optional start number. If omitted on import, Radio-Oracle assigns the next available start number.
- `first_name`: Required competitor first name.
- `last_name`: Required competitor last name.
- `category`: Optional category name. If omitted, the competitor is imported without an assigned category.
- `gender`: `0` for Men, `1` for Women.
- `birth_year`: Optional four-digit birth year.
- `club`: Optional club name.
- `index`: Optional callsign or registration index.
- `start_time`: Optional start time relative to the race start, formatted as `HH:MM` or `MM:SS` according to the app's duration parser.
- `si_rent`: `1` when the SI card is rented, otherwise `0`.
- `preferred_start_group`: Optional start third assignment for championship-style draws. Use `1`, `2`, or `3`; leave blank for no assignment.
- `bib_number`: Optional visible bib number. If omitted, Radio-Oracle falls back to `index`.
- `call_sign`: Optional call sign. Duplicate checks are case-insensitive.

Example:

```text
si_number;start_number;first_name;last_name;category;gender;birth_year;club;index;start_time;si_rent;preferred_start_group;bib_number;call_sign
123456;7;Test;Runner;M21;0;1985;OK Test;OK001;10:00;0;2;B007;RUN
;8;Practice;Attendee;;1;;Local Club;;;0;;;
```

Fields containing semicolons, quotes, or line breaks are quoted with double quotes. Quotes inside a quoted field are doubled.

## Compatibility Profiles

Radio-Oracle's canonical competitor CSV is the round-trip format for preserving
Radio-Oracle competitor fields. Duplicate SI numbers, registration indexes, bib
numbers, and call signs are rejected by default.

Radio-Oracle also accepts an ARDFEvent-compatible registration CSV profile for
preregistration files with this semicolon-delimited header:

```text
Jmeno;Prijmeni;Registrace;SI;Kategorie
```

The Czech header used by ARDFEvent is:

```text
Jméno;Příjmení;Registrace;SI;Kategorie
```

That profile should map:

- `Jmeno` / `Jméno` to `first_name`.
- `Prijmeni` / `Příjmení` to `last_name`.
- `Registrace` to `index`.
- `SI` to `si_number`.
- `Kategorie` to `category`.

ARDFEvent-compatible import is intentionally an alternate import profile, not a
replacement for the canonical Radio-Oracle CSV export format.

On desktop, ARDFEvent-compatible imports use preregistration update behavior:
when a nonblank `Registrace` value already matches an existing competitor index,
the existing competitor is updated instead of creating a duplicate. If the
incoming SI number belongs to a different competitor, the import is rejected.
Missing categories create placeholder categories and are reported as warnings;
empty categories leave competitors category-less and are also reported.

## Starts CSV

The starts CSV importer accepts the compact three-column start-list format and
the longer start-list rows exported by Radio-Oracle. Neither format includes a
header row.

The compact import field order is:

```text
start_number;start_time;si_number
```

The exported field order is:

```text
start_number;last_name;first_name;category;reserved;start_time;index;reserved;club;si_number
```

Radio-Oracle matches prior starts to current competitors by `si_number` when
available. If `si_number` is blank, `start_number` is used as a fallback.
Because start numbers may change between days, SI numbers give the most reliable
multi-day fairness history.
