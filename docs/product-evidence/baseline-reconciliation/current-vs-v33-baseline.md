# Current Baseline vs Historical V3.3

## Decision

Doctor's target-memory baseline is now reproduced. Patient and PetClinic do
not currently have admissible historical comparators.

This is a baseline-only reconciliation. No V1, V2, or candidate comparison is
included.

## Matrix

| Service | Historical source | Current status | Verdict |
|---|---|---|---|
| Patient | Phase 31D recovered summaries | Original standalone artifact unavailable | `HISTORICAL_COMPARATOR_NOT_RECOVERABLE` |
| Doctor | Phase 32K-I B0 raw runs | Three restored-runtime B0 runs accepted | `TARGET_MEMORY_BASELINE_REPRODUCED` |
| PetClinic customers | Phase 33M recovered evidence | Archived B0 contains JMOA output and application drift | `HISTORICAL_B0_INVALID_CONTAMINATED` |

## Doctor Correction

The first current Doctor reconstruction used a full JDK and produced a
three-run anonymous/private-dirty distribution above the historical range.
That result was real for that runtime, but it did not reproduce the historical
Dockerfile.

The HMS Doctor Dockerfile was recovered and showed that Phase 32 used a custom
`jlink` runtime in a distroless final image. The preserved Phase 32K application
archive then identified the compatible runtime as Temurin OpenJDK `26+35`.

After rebuilding that custom JRE:

- all three current anonymous-PSS values are inside the historical range;
- all three current Private_Dirty values are inside the historical range;
- file-normalized current median PSS is inside the historical PSS range;
- the exact B0 JAR, 80-request workload, compact-header base archive, and
  20-second capture timing are verified.

Current median target metrics:

| Metric | Historical median | Restored median | Status |
|---|---:|---:|---|
| PSS | 336,484 KB | 329,020 KB | File mapping differs |
| Anonymous PSS | 290,140 KB | 292,312 KB | Inside historical range |
| Private_Dirty | 290,148 KB | 292,312 KB | Inside historical range |
| File-normalized PSS | n/a | 339,036 KB | Inside historical PSS range |
| `memory.current` | 424,136,704 B | 302,432,256 B | Not comparable |
| Startup | 33,700 ms | 41,737 ms | Not reproduced |

See
[Doctor Phase 32 restored runtime baseline](doctor-phase32-restored-runtime-baseline.md)
for the tuple, all values, caveats, failures, and command-ledger references.

## Remaining Boundary

Doctor baseline reconciliation does not repair the missing Patient artifact or
the contaminated PetClinic historical baseline. Those services require a new
clean frozen campaign or recovery of a valid historical artifact.

No candidate run was executed as part of this correction.
