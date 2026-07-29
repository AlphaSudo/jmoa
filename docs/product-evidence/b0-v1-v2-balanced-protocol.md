# Direct B0/V1/V2 Balanced Protocol

The final product question is measured directly:

```text
B0 = same service without JMOA
V1 = accepted original JMOA transform
V2 = accepted V1 transform plus the accepted V2 reducer
```

Historical results are diagnostic references. They are never added together to manufacture B0-to-V2.

## Observation Unit

Every observation creates a fresh support stack, launches one target JVM, runs the frozen warmup and workload, captures claim metrics before diagnostics, writes one complete command/response ledger, and tears everything down.

## Balanced Orders

| Block | Position 1 | Position 2 | Position 3 |
|---:|---|---|---|
| 1 | B0 | V1 | V2 |
| 2 | B0 | V2 | V1 |
| 3 | V1 | B0 | V2 |
| 4 | V1 | V2 | B0 |
| 5 | V2 | B0 | V1 |
| 6 | V2 | V1 | B0 |

This yields six direct deltas for B0-to-V1, V1-to-V2, and B0-to-V2 while placing each artifact twice in every execution position.

## Headline Gate

B0-to-V2 requires 18/18 valid final observations, at least 4/6 paired PSS wins, median PSS at or below -4,096 KB, exact bootstrap 95% upper bound below zero, median Private Dirty at or below -1,024 KB, median `memory.current` at or below -1,048,576 bytes, zero semantic errors, V2-C `CONFIRMED_WIN`, and passing V2-D attribution.

Valid losses remain. Only objectively invalid observations can be rerun, and every attempt remains in the private evidence archive.

See the executable [adoption guide](../adoption/README.md).
