# Historical Runtime Replay

## PetClinic Phase 33M

Status: **HISTORICAL_RUNTIME_DRIFT**

The original Phase 33M runner was executed from an isolated workspace against
the frozen image IDs and original artifact hashes. It used the original
exploded-Boot, SerialGC, `-Xshare:off`, `MALLOC_ARENA_MAX=1`, 20-second warmup,
5-second settle, and 81-request workload.

| Metric | Original accepted median | 2026-07-19 replay median |
|---|---:|---:|
| PSS delta | -4,758 KB | +8,647 KB |
| Private_Dirty delta | -4,904 KB | +8,780 KB |
| memory.current delta | -4,849,664 B | +2,166,784 B |
| paired wins | 3/3 | 0/3 |

The replay still reduced loaded classes by roughly 154 and retained-heap bytes
did not grow materially. The direction changed through heap page residency and
anonymous memory. PetClinic is stopped before a new three-arm product campaign
until the runtime drift is isolated.

## Doctor

The current V2-K D2-to-D2R confirmation is the surviving accepted replay path:
3/3 wins, median PSS -5,156 KB, and separately trained artifact-specific CDS
archives. D2 is an optimized comparator, not B0.

## Patient

The accepted corrected V1-to-V2 artifacts remain confirmed under the frozen
base-CDS and no-CDS protocols. The direct B0 screen did not use the accepted
corrected V2 SHA, so it is not a replay of that result.
