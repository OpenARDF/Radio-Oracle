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
