# V2 Final Performance Acceptance

Decision: `PERFORMANCE_GATES_PASSED`

## Frozen Protocol

Public PetClinic customers-service, source revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967`, exploded Boot/JarLauncher,
`NO_CDS_LOW_DIRTY`, `MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden/javaagent,
27 endpoints x 3 rounds, 20-second warmup, and 5-second settle.

Every variant starts from a cold page-cache precondition. Pair order is balanced
`B-C`, `C-B`, `B-C`, and cgroup `memory.stat` is captured with the JVM evidence.

## Results

| Gate | Valid runs | Paired wins | Median PSS | Median Private_Dirty | Median memory.current | Classification |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| B0 -> final V2 | 6/6 | 2/3 | -8,956 KB | -8,620 KB | -11,247,616 B | `SUBSTANTIAL_WIN` |
| finalized V1 -> final V2 | 6/6 | 3/3 | -4,812 KB | -4,512 KB | -6,791,168 B | `SUBSTANTIAL_WIN` |

V2-C reports `CONFIRMED_WIN` for both comparisons. V2-D attributes both
primarily to `HEAP_PAGE_TOUCH_REDUCTION`, with supporting anonymous writable
and metaspace reductions.

## Superseded Run

The previous B0-to-V2 run is retained but no longer drives release acceptance.
It used fixed order without a page-cache reset: B0 recorded zero block IO while
V2 incurred roughly 60 MB, so `memory.current` included an unequal file-cache
charge. This is a verified protocol defect, not an excluded losing pair.

## Claim Boundary

These results support a substantial final V2 memory win for the exact public
customers protocol above. They do not establish startup improvement, a
universal Spring Boot result, or transfer across launch/runtime policies.
