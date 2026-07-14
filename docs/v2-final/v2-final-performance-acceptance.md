# V2 Final Performance Acceptance

Decision: `INCREMENTAL_GATE_PASSED_DIRECT_BASELINE_NOT_REPRODUCED`

## Frozen Protocol

Public PetClinic customers-service, exploded Boot/JarLauncher,
`NO_CDS_LOW_DIRTY`, `MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden/javaagent,
and the corrected 27-endpoint, three-round workload. Each run uses fresh
containers; memory pairs do not enable class-load logging.

## Reproducible RC2 Results

| Gate | Valid runs | Paired wins | Median PSS | Median Private_Dirty | Median memory.current | Classification |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| finalized V1 -> final V2 | 6/6 | 2/3 | -6,012 KB | -5,708 KB | -8,081,408 B | `CONFIRMED_WIN` |
| B0 -> final V2 (five-pair replication) | 10/10 | 2/5 | +585 KB | +844 KB | -1,363,968 B | `MIXED_METRICS_NEEDS_RERUN` |

V2-C confirms the incremental V1-to-V2 comparison. V2-D attributes its median
movement primarily to `HEAP_PAGE_TOUCH_REDUCTION`, with lower anonymous-RW PSS
as supporting evidence. No startup claim is made.

## Claim Boundary

The RC2 release may claim a confirmed incremental V1-to-V2 memory reduction for
this exact PetClinic protocol. It may not claim that final V2 directly beats B0
until a reproducible direct-baseline confirmation passes. The older B0-to-V2
result remains historical provenance only because its raw capture is absent.
