# 09 Run Six Balanced Blocks

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

Run all six artifact orders exactly once:

```text
B0 V1 V2
B0 V2 V1
V1 B0 V2
V1 V2 B0
V2 B0 V1
V2 V1 B0
```

This produces 18 independent final sessions. Each artifact appears twice in
each execution position.

For every block compute direct deltas:

```text
B0 -> V1
V1 -> V2
B0 -> V2
```

Do not add a historical B0-to-V1 median to a new V1-to-V2 result. The product
claim is the direct B0-to-V2 comparison in the same block.

Primary statistics:

```text
six block deltas
median and mean
minimum and maximum
median absolute deviation
paired wins
exact 6^6 bootstrap interval of the median
```

The execution-position additive model is diagnostic only. It may show residual
period/order sensitivity, but it does not replace paired medians.

## Replacement Rule

Only invalid sessions may be retried. Never replace a valid run because it
loses, looks unusual, or changes a median.
