# V2 RC2 Frozen-Image Replication

## Purpose

This report records fresh runtime measurements against the immutable images and
artifact SHA-256 values in `v2-performance-artifact-freeze.json`. It does not
replace historical evidence. It establishes what is reproducible from the
currently recovered frozen runtime artifacts.

## Protocol

- Service: Spring PetClinic customers-service
- Launch mode: `EXPLODED_BOOT_APP`
- Runtime policy: no CDS, no AppCDS, no Leyden, no runtime javaagent,
  `MALLOC_ARENA_MAX=1`
- Workload: corrected 27 endpoints x 3 rounds (81 requests)
- Page cache: dropped before every variant
- Pair ordering: alternating baseline-first/candidate-first
- Every sample: fresh Podman network and config, discovery, and customers
  containers

## B0 to V2 Five-Pair Replication

The original three-pair result remains a historical audited observation. Its
raw run folder was no longer present after the release build, so this new run
was performed against the recovered exact B0 and V2 image IDs. All ten runs
were V2-C valid.

| Pair | PSS delta KB | Private_Dirty delta KB | memory.current delta bytes | Pair win |
|---:|---:|---:|---:|---:|
| 1 | +585 | +844 | -1,363,968 | no |
| 2 | +3,197 | +3,376 | +1,245,184 | no |
| 3 | -9,714 | -9,428 | -11,870,208 | yes |
| 4 | -8,354 | -8,448 | -11,055,104 | yes |
| 5 | +5,146 | +4,580 | +2,600,960 | no |

Median deltas: PSS `+585 KB`, Private_Dirty `+844 KB`, and
`memory.current -1,363,968 bytes`; paired wins `2/5`.

V2-C verdict: `MIXED_METRICS_NEEDS_RERUN`.

V2-D identified a high-confidence `HEAP_PAGE_TOUCH_GROWTH` signal, with class
count savings and anonymous-RW reduction insufficient to overcome the mixed
PSS result. Therefore this fresh reproduction is **not a confirmed B0 to V2
runtime win**.

## V1 to V2 Three-Pair Replication

All six runs were V2-C valid.

| Pair | PSS delta KB | Private_Dirty delta KB | memory.current delta bytes | Pair win |
|---:|---:|---:|---:|---:|
| 1 | -10,396 | -10,212 | -12,705,792 | yes |
| 2 | -6,012 | -5,708 | -8,081,408 | yes |
| 3 | -576 | -996 | -3,096,576 | no |

Median deltas: PSS `-6,012 KB`, Private_Dirty `-5,708 KB`, and
`memory.current -8,081,408 bytes`; paired wins `2/3`.

V2-C verdict: `CONFIRMED_WIN`.

V2-D attributes the result primarily to `HEAP_PAGE_TOUCH_REDUCTION`, with a
secondary anonymous-RW reduction. Heap used and histogram bytes were near flat,
so the result must not be described as retained-object shrinkage.

## Claim Boundary

The reproducible public runtime claim is now limited to the incremental
`V1 -> V2` comparison above. The earlier B0 to V2 result is retained as
historical audited evidence, but is not an RC2 reproducible performance claim
until a future controlled replication confirms it.
