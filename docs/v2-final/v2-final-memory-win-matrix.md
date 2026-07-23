# V2 Final Memory-Win Matrix

## Reproducible Public Acceptance

| Comparison | Valid runs | Paired wins | Median PSS | Median Private_Dirty | Median memory.current | Verdict |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| customers finalized V1 -> final V2 | 6/6 | 2/3 | -6,012 KB | -5,708 KB | -8,081,408 B | `CONFIRMED_WIN` |

This comparison used `EXPLODED_BOOT_APP`, `NO_CDS_LOW_DIRTY`,
`MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden/javaagent, 27 endpoints x 3 rounds,
balanced pair order, and a cold page-cache precondition before every variant.

V2-C accepted all six runs. V2-D attributes the incremental result primarily
to heap-page-touch reduction with a secondary anonymous-RW reduction; it is not
a retained-object or loaded-class-count claim.

## Historical B0 to V2 Result and Fresh Replication

The original corrected three-pair B0 -> V2 result remains an audited historical
observation: 2/3 paired wins, median PSS -8,956 KB, Private_Dirty -8,620 KB,
and memory.current -11,247,616 B. Its raw capture folder was not retained after
the release build.

A fresh five-pair run using the recovered exact B0 and V2 image IDs produced:

| Comparison | Valid runs | Paired wins | Median PSS | Median Private_Dirty | Median memory.current | Verdict |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| customers B0 -> final V2 frozen-image replication | 10/10 | 2/5 | +585 KB | +844 KB | -1,363,968 B | `MIXED_METRICS_NEEDS_RERUN` |

V2-D attributes this mixed result primarily to heap-page-touch growth. The
historical B0 -> V2 outcome is therefore retained for provenance but is **not a
reproducible RC2 public performance claim**.

## Audited Direct Campaign Gate

The final signed PetClinic campaign started with the runtime baseline, auxiliary
services, warmup, workload, and memory capture before admitting any V2 arm.
Two unchanged B0 same-artifact executions produced:

| Campaign | Valid B0 arms | Median absolute PSS noise | Median absolute Private_Dirty noise | Median absolute memory.current noise | Verdict |
| --- | ---: | ---: | ---: | ---: | --- |
| `20260723T190749Z` | 4/4 | 4,849.5 KB | 4,772 KB | 4,964,352 B | `STOPPED_B0_RUNTIME_VARIANCE` |
| `20260723T192107Z` | 4/4 | 5,580.5 KB | 5,300 KB | 5,181,440 B | `STOPPED_B0_RUNTIME_VARIANCE` |

All eight arms completed 81 requests with zero errors and passed the frozen
swap and PSI gates. The positional direction reversed between executions. The
campaign therefore emitted `ENVIRONMENT_VARIANCE_TOO_HIGH` and did not run V2
controls or B0/V2 pairs. No direct PetClinic product delta is reported from
this campaign.

## Scope-Bound Confirmations

| Comparison | Paired wins | Median PSS | Median Private_Dirty | Median memory.current | Boundary |
| --- | ---: | ---: | ---: | ---: | --- |
| public visits baseline -> raw reducer | 3/3 | -2,012 KB | -1,680 KB | -1,712,128 B | public visits no-CDS protocol only |
| private Doctor D2 -> D2R | 3/3 | -5,156 KB | -5,212 KB | -6,975,488 B | private corrected fat-JAR/CDS protocol only |

No startup win, universal Spring Boot win, or cross-runtime transfer is claimed.
