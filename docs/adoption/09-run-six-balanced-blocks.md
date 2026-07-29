# 09 Run Six Balanced Blocks

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
