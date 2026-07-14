# V2 Final Memory-Win Matrix

The final release configuration passed both fresh public acceptance gates under
the frozen, cache-controlled customers-service protocol.

## Primary Acceptance

| Comparison | Valid runs | Paired wins | Median PSS | Median Private_Dirty | Median memory.current | Verdict |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| customers B0 -> final V2 | 6/6 | 2/3 | -8,956 KB | -8,620 KB | -11,247,616 B | `SUBSTANTIAL_WIN` |
| customers finalized V1 -> final V2 | 6/6 | 3/3 | -4,812 KB | -4,512 KB | -6,791,168 B | `SUBSTANTIAL_WIN` |

Both comparisons used `EXPLODED_BOOT_APP`, `NO_CDS_LOW_DIRTY`,
`MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden/javaagent, 27 endpoints x 3 rounds,
balanced pair order, and a cold page-cache precondition before every variant.

## Scope-Bound Confirmations

| Comparison | Paired wins | Median PSS | Median Private_Dirty | Median memory.current | Boundary |
| --- | ---: | ---: | ---: | ---: | --- |
| public visits baseline -> raw reducer | 3/3 | -2,012 KB | -1,680 KB | -1,712,128 B | public visits no-CDS protocol only |
| private Doctor D2 -> D2R | 3/3 | -5,156 KB | -5,212 KB | -6,975,488 B | private corrected fat-JAR/CDS protocol only |

## Interpretation

V2-C accepted all twelve fresh customers runs. V2-D attributes both final
customers wins primarily to lower heap page touch, with supporting reductions
in anonymous writable mappings and committed metaspace. The V1-to-V2 result has
no loaded-class-count change, so the incremental reducer claim is not a
class-count claim.

No startup win, universal Spring Boot win, or cross-runtime transfer is claimed.
