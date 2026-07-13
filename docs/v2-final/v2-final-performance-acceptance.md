# V2 Final Performance Acceptance

This file records the final public performance gate requested before a
`v2.0.0-rc1` release.

## Required Comparisons

The release acceptance gate requires direct evidence for:

```text
B0 = unoptimized public baseline
V1 = finalized V1/full-P2 artifact
V2 = finalized V2 product artifact
```

The required comparisons are:

```text
B0 -> V2
V1 -> V2
```

The release must not add separate historical medians to claim a direct
`B0 -> V2` result.

## Frozen Public Customers Artifacts

| Variant | Meaning | SHA-256 | Evidence |
| --- | --- | --- | --- |
| B0 | customers unoptimized baseline | `4952EF9306C732846BFAE0FAE6A67BE2F9B8509644B3396B8247215A03E5589D` | local phase33 baseline JAR |
| V1 | customers full-P2 | `314761904021A75EF1BD114B28BBE15FCAFE31C9F21C71577ABF47CECA33A92C` | Phase 33M |
| V2 | customers full-P2 plus raw LVT/LVTT reducer | `007F1796B83FCC2217A57A6975EF5CAFD7494A5EF050A7F5261C088B63C6CC2F` | fresh V2 final direct run |

Protocol:

```text
service: spring-petclinic-customers-service
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS/Leyden: off
runtime javaagent: absent
workload: corrected 27 endpoints x 3 rounds
```

## Fresh B0 -> V2 Direct Run

Run date: July 13, 2026.

Location of raw local evidence:

```text
target/v2-final/customers-b0-v2-direct
```

Result:

```text
status: NOT_CONFIRMED
valid runs: 6/6
paired wins: 1/3
median PSS delta: +274 KB
median Private_Dirty delta: +464 KB
median memory.current delta: +225,280 bytes
workload errors: 0
```

Pair details:

| Pair | PSS delta | Private_Dirty delta | memory.current delta | Gate |
| ---: | ---: | ---: | ---: | --- |
| 1 | `-16,988 KB` | `-16,908 KB` | `-32,989,184 bytes` | pass |
| 2 | `+274 KB` | `+464 KB` | `+225,280 bytes` | fail |
| 3 | `+9,389 KB` | `+9,388 KB` | `+9,363,456 bytes` | fail |

Classification:

```text
RELEASE_PERFORMANCE_GATE_FAILED
```

The final V2 product artifact is not currently confirmed as a direct substantial
win over the public customers unoptimized baseline.

## V1 -> V2 Incremental Result

Existing V2-I evidence remains valid as an incremental comparison:

```text
comparison: full P2 vs full P2 + V2-I raw LVT/LVTT reducer
valid runs: 6/6
paired wins: 2/3
median PSS delta: -4,467 KB
median Private_Dirty delta: -4,208 KB
median memory.current delta: -4,493,312 bytes
```

Classification:

```text
CONFIRMED_INCREMENTAL_WIN
```

## B0 -> V1 Context

Phase 33M remains the historical public no-CDS full-P2 confirmation:

```text
paired wins: 3/3
median PSS delta: -4,758 KB
median Private_Dirty delta: -4,904 KB
median memory.current delta: -4,849,664 bytes
```

This is context, not a substitute for the fresh direct `B0 -> V2` gate.

## Release Decision

Decision:

```text
READY_AFTER_LISTED_BLOCKERS
```

P0 blocker:

```text
FINAL_PUBLIC_B0_V2_NOT_CONFIRMED
```

The release can continue with scope freeze, audit, packaging, and clean-clone
work. It must not publish a `v2.0.0` performance headline saying final V2
substantially beats the public baseline until a direct `B0 -> V2` confirmation
passes.

