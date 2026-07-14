# V2 Schema Freeze

Status: `COMPLETE`

The shipped V2 report families are registered in
[`v2-schema-registry.json`](v2-schema-registry.json) and checked by
`scripts/check-v2-schema-compatibility.ps1` against committed historical
fixtures.

Compatibility policy:

- a reader may ignore unknown optional fields within a known metadata version;
- required fields may not be removed or retyped within that version;
- an unknown major metadata version must be rejected rather than guessed;
- an incompatible producer change requires a new `metadataVersion`.

Java unit/integration tests remain the behavioral schema tests for live report
writers; the PowerShell registry check protects the public historical fixtures
and release documentation from drift.
