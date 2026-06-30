# Measurement Methodology

JMOA memory claims use paired measurements and median deltas.

## Primary Metrics

- smaps PSS
- smaps Private_Dirty
- cgroup `memory.current`

## Supporting Diagnostics

- Native Memory Tracking
- smaps region breakdown
- `GC.class_histogram`
- heap committed and heap used
- loaded class counts
- dynamic class-load origin logs
- startup timing
- workload error counts

## Pairing

A publishable comparison should alternate fresh baseline and optimized runs:

```text
B1 -> O1
B2 -> O2
B3 -> O3
```

The measurement must document:

- artifact hashes,
- runtime mode,
- CDS/no-CDS state,
- javaagent absence,
- workload correctness,
- adapter reference status,
- runtime-origin proof.

## Invalid Runs

Runs are invalid if they contain workload errors, stale containers, wrong
deployment mode, unexpected CDS state, runtime javaagent contamination, missing
adapter references, or unproven optimized origins.
