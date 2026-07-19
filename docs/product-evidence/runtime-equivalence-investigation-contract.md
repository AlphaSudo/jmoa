# Runtime Equivalence Investigation Contract

Status: frozen before reconciliation execution.

## Objective

Determine whether the clean no-JMOA `B0` and final V2 artifacts were built and
measured inside runtime-equivalent source, dependency, JVM, image, archive,
support-stack, workload, and capture lineages.

The existing direct results are retained, but the aggregate adoption verdict is
`DIRECT_PRODUCT_MATRIX_UNDER_RECONCILIATION` until this contract is closed.

## Invariants

- no optimizer behavior change;
- no threshold change;
- no favorable-run selection;
- no historical-median addition;
- no protocol redesign before historical replay;
- no hand-edited baseline without byte-level provenance;
- no replacement of a valid losing run;
- no newly rebuilt B0 compared with a frozen V2 unless source and dependency
  fingerprints establish one coherent lineage.

## Allowed Corrections

Only a proven mismatch in artifact, source revision, dependency graph, runtime
image, JVM flags, CDS archive, support stack, workload, cache precondition,
capture timing, or evidence tooling may authorize a corrected run.

Every correction must cite an audited command ID and before/after hashes.

## Execution Order

1. recover the accepted historical commands and artifacts;
2. classify command, artifact, and runtime completeness;
3. replay the accepted V1-to-V2 path without redesign;
4. measure same-artifact noise;
5. establish coherent B0/V1/V2 lineage;
6. run rotated three-arm blocks;
7. validate with V2-C and explain with V2-D;
8. publish one exact terminal state per service.

## Terminal States

```text
DIRECT_PRODUCT_WIN_CONFIRMED
DIRECT_PRODUCT_EFFECT_NOT_CONFIRMED
HISTORICAL_RUNTIME_NOT_REPRODUCIBLE
ARTIFACT_LINEAGE_INVALID
RUNTIME_PROTOCOL_MISMATCH
ENVIRONMENT_VARIANCE_TOO_HIGH
```

Raw commands, fingerprints, private paths, and private service data remain
under `target/`. Only sanitized ledgers and conclusions may be committed.
