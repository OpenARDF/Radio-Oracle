# Control identities and Race File format 8

The race control catalog is authoritative on desktop and Android. A control has a stable, opaque ID, an SI station number, a role, and a display label (`publicLabel` when set, otherwise `label`). The ID does not change when a control is renamed or assigned another station. Neither the ID's spelling nor a previous label describes its present meaning.

Courses reference control IDs. Stored SI numbers and strings on course assignments are derived fields, not alternative definitions of a control. If a file contains both assignment rows and `publicControlIds`, their memberships must agree. Missing references, duplicate control IDs, and duplicate SI assignments in an applied catalog are errors. Incomplete drafts may retain station conflicts while being edited; they remain separate from the applied race.

For radio orienteering, displayed assignment order comes from `EventAssignedControlOrder`, which uses current control labels and roles. Ordered orienteering preserves its required course order. The calculated ideal route is separate from both.

A station represented by a control must not also have an independent entry in the race alias table. Writes remove those redundant aliases. The shared `EventControlCatalog.resolvedAliases` derives the aliases required by Android's native database and result formats from current controls. Any remaining alias is only an auxiliary label for a station outside the control catalog; it cannot supply a missing course control or override a catalogued station. An alias attached to a historical punch is display metadata, not a course definition.

Both desktop and Android use Race File format 8, including the Save Android Race File action. Ordinary file opening rejects earlier formats and legacy Android race-backup JSON. There is no automatic reconstruction from legacy station numbers, aliases, or missing references. A `.json` or `.ardfjs` filename does not override the format declared inside it. Old applications must be updated before receiving format 8.

`EventProjectFileJson` validates reads and normalized writes. Text names in a KML/GPX import are inputs to an explicit import review; after acceptance, stored assignments use control IDs. Existing shared import, ordering, and binding code remains responsible for those operations.

Obsolete files should be retained unchanged. Recovery into a new format requires an explicit, verified conversion of the intended control catalog and assignments, saved as a separate file. Do not silently rewrite the original or guess intended controls from obsolete aliases.
