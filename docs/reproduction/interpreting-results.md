# Interpreting Results

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

All deltas are candidate minus comparator. Negative memory deltas favor the
candidate.

## Read In This Order

1. **Validity:** are all required run files present, arithmetic sound, workload
   errors zero, and artifact/policy proofs correct?
2. **Comparator:** is the result baseline-to-candidate, V1-to-V2, or an
   incremental reducer comparison?
3. **Protocol:** launch shape, CDS policy, allocator, JDK, workload, cache state,
   and capture point define the claim.
4. **Pair table:** retain every valid winning and losing pair.
5. **Median and denominators:** report `2/3`, not "most".
6. **Attribution:** distinguish observed category movement from interpretation.
7. **Boundary:** list what the result does not establish.

## Historical example

Observed for Patient stock base CDS:

```text
valid runs: 6/6
paired wins: 3/3
median PSS: -8,279 KB
median Private_Dirty: -8,444 KB
median memory.current: -8,523,776 B
```

Interpretation: heap PSS and anonymous writable pages decreased; V2-D ranked
heap page-touch reduction first.

Not claimed: fewer retained business objects, class-count causality, startup
improvement, or benefit from a Patient application archive.

## Non-Claimable Outcomes

`MIXED_METRICS_NEEDS_RERUN`, `INVALID_EVIDENCE`, a diagnostic screen, an
artifact-size reduction, or semantic smoke alone cannot support a runtime
memory claim. A valid regression should be published as a negative result.


## Current release interpretation

PetClinic 2.1 is the accepted packaging-inclusive direct deployment contrast.
Patient 2.15 is a direct B0/composed Leyden comparison with an approved
post-observation calibration exception. Its RAM observation and conditional
bound do not establish passing performance gates or uploaded-binary parity.
The historical stock-base-CDS example above has a different comparator.
