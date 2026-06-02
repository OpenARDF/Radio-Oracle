# Competitor CSV

Radio-Oracle imports and exports competitor registration data as semicolon-delimited CSV.

The canonical header is:

```text
si_number;start_number;first_name;last_name;category;gender;birth_year;club;index;start_time;si_rent
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

Example:

```text
si_number;start_number;first_name;last_name;category;gender;birth_year;club;index;start_time;si_rent
123456;7;Test;Runner;M21;0;1985;OK Test;OK001;10:00;0
;8;Practice;Attendee;;1;;Local Club;;;0
```

Fields containing semicolons, quotes, or line breaks are quoted with double quotes. Quotes inside a quoted field are doubled.

## Compatibility Profiles

Radio-Oracle's canonical competitor CSV is the round-trip format for preserving
Radio-Oracle competitor fields.

Future import work should also support an ARDFEvent-compatible registration CSV
profile for preregistration files with this semicolon-delimited header:

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
