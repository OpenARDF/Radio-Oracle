# Radio-O Standards Compatibility Policy

Radio-Oracle treats
[`radio-o-standards`](https://github.com/AROB-CR/radio-o-standards) as the
upstream contract for shared Radio Orienteering interchange formats.

## Policy

Do not intentionally move Radio-Oracle farther away from `radio-o-standards`
without first submitting a pull request to `radio-o-standards` that proposes the
needed standard change.

This applies to changes in:

- ARDF JSON field names, value types, enums, time formats, and document shape;
- ARDF XML namespace usage, extension elements, IOF XML mapping, and schema
  locations;
- race, category, competitor, control, alias, punch, result, start-list, and
  live/final result interchange semantics;
- import/export behavior that would make newly produced files harder for other
  standards-aware tools to read.

## IOF XML Extension Rule

Radio-Oracle targets schema-valid IOF XML 3.0 for IOF interchange. Radio
Orienteering concepts that IOF XML does not define, including transmitter roles,
finish and spectator beacons, exclusion zones, frequency, modulation, antenna
polarization, power levels, and transmitter schedules, must use IOF
`Extensions` with a non-IOF namespace instead of new IOF-core elements.

The planned Radio Orienteering extension namespace is:

```xml
xmlns:ro="https://openardf.org/xml/radioorienteering/iof-extensions/1.0"
```

The namespace URI uses an OpenARDF-controlled domain for stable ownership, but
the path names the sport vocabulary. Do not use IOF-owned namespace names for
Radio-Oracle or Radio Orienteering extensions unless IOF publishes and owns that
vocabulary.

## Required workflow

Before merging a Radio-Oracle change that changes interchange behavior:

1. Compare the proposed behavior with the current `radio-o-standards` schema,
   examples, and README files.
2. If the behavior is compatible, include the compatibility rationale in the
   Radio-Oracle change notes or PR description.
3. If the behavior is intentionally incompatible or requires a new standard
   capability, open a `radio-o-standards` pull request first.
4. Link the standards PR from the Radio-Oracle change.
5. Prefer adding or updating Radio-Oracle golden-file tests using
   `radio-o-standards` examples or fixtures.

## Deployment gate

Before deploying any Android or desktop build, inspect the release candidate for
drift from the current `radio-o-standards` repository.

The deployment check must:

1. Review all release-candidate changes that affect ARDF JSON, ARDF XML, IOF
   XML mapping, import/export behavior, or standards-facing event data.
2. Compare those changes with the current `radio-o-standards` schemas, examples,
   and README files.
3. Record one of these outcomes in the release notes, deployment checklist, or
   release PR:
   - `compatible`: no new drift found;
   - `standards-pr-linked`: new drift or new capability is covered by a linked
     `radio-o-standards` pull request;
   - `not-applicable`: the release candidate does not touch standards-facing
     behavior.
4. Block deployment if new drift is found and no `radio-o-standards` pull
   request has been submitted.

## Existing Drift

This policy is forward-looking. Existing Radio-Oracle differences from
`radio-o-standards` should be tracked and reduced as import/export code moves
into shared Kotlin. Existing drift is not a reason to add new drift without a
standards PR.

## Exceptions

Private Event Files such as `.rom.json` may use Radio-Oracle-specific
metadata when they are clearly not advertised as ARDF JSON or ARDF XML
interchange files. Even then, Event File fields that duplicate standard
interchange concepts should stay easy to map back to `radio-o-standards`.
