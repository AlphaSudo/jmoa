# 05 Prove Lineage

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

Prove this graph before runtime:

```text
one source revision
  -> clean B0
  -> accepted V1 transform
  -> accepted V1 artifact
  -> V2 reducer
  -> V2 artifact
```

Compare:

```text
source revision
application entry names
application class names
resources and configuration
dependency coordinates/names
dependency content hashes
Boot loader
manifest and packaging
generated/AOT output
database migration resources
```

Whole-archive equality is the wrong test: V1 is supposed to transform content.
Use structured fingerprints and the build chain to distinguish expected JMOA
changes from unrelated drift.

The historical three-artifact forensic audit reports `LINEAGE_VALID` for Doctor, Patient, and
PetClinic. That means their losing or uncertain results cannot be dismissed as
the wrong source universe.

## Decisions

Use one of:

```text
LINEAGE_VALID
B0_SOURCE_MISMATCH
V1_SOURCE_MISMATCH
DEPENDENCY_UNIVERSE_MISMATCH
GENERATED_OUTPUT_MISMATCH
PACKAGING_MISMATCH
```

Any mismatch stops the campaign. Fix lineage, then freeze a new campaign
directory.
