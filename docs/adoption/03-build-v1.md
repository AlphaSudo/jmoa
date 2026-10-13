# 03 Build V1

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

V1 is the accepted JMOA lambda/adapter artifact before the V2 metadata reducer.
It must be derived from the same frozen source universe as B0.

Record:

```text
plugin coordinates and SHA
effective plugin parameters
transformation report
rewritten and skipped classes/sites
generated adapters
runtime-library version
preservation failures
artifact and layer SHA-256 values
```

Do not select V1 because it produced a good historical run. Select it by the
frozen build and safety contract.

V1 may intentionally differ from B0:

- rewritten application/dependency class bytes;
- generated `JmoaPkgAdapters`;
- the JMOA runtime library;
- packaging entries required by the transform.

Those differences are not lineage defects when their derivation is recorded.
Unexplained resources, source revisions, or dependency-coordinate changes are
defects.

## Gate

Require zero preservation failures, bytecode verification, a readable
deployment artifact, and a transformation report. Runtime exercise is proven
later; artifact presence alone is insufficient.
