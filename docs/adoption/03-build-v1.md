# 03 Build V1

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
