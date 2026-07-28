# patient-service Direct B0/V1/V2 Result

- Terminal outcome: **B0_TO_V1_NOT_REPRODUCED**
- Protocol: `PATIENT_B0_V1_V2_BALANCED_V1`
- Runtime: `SPRING_BOOT_FAT_JAR` / `JDK_BASE_CDS_LOW_DIRTY`
- Final observations: 18/18 valid; semantic errors: 0

| Comparison | PSS median | PSS wins | PSS 95% CI | Private Dirty median | memory.current median | V2-C | V2-D |
|---|---:|---:|---:|---:|---:|---|---|
| B0_TO_V1 | 2018.5 KB | 1/6 | [-3053.5, 6742.5] KB | 2036 KB | 2058240 B | MIXED_METRICS_NEEDS_RERUN | True |
| V1_TO_V2 | -2409 KB | 4/6 | [-3969, 2075] KB | -2408 KB | -2267136 B | CONFIRMED_WIN | True |
| B0_TO_V2 | 1663.5 KB | 3/6 | [-6629, 6370] KB | 1454 KB | 1552384 B | MIXED_METRICS_NEEDS_RERUN | True |

## B0 To V2 Blocks

| Block | Order | PSS | Private Dirty | memory.current |
|---:|---|---:|---:|---:|
| 1 | B0,V1,V2 | 4679 KB | 4432 KB | 4534272 B |
| 2 | B0,V2,V1 | -2834 KB | -2816 KB | -2613248 B |
| 3 | V1,B0,V2 | 6380 KB | 6512 KB | 6823936 B |
| 4 | V1,V2,B0 | 6360 KB | 6320 KB | 6500352 B |
| 5 | V2,B0,V1 | -10424 KB | -10460 KB | -10715136 B |
| 6 | V2,V1,B0 | -1352 KB | -1524 KB | -1429504 B |

## Attribution

- smaps/NMT reconciliation: `NMT_VISIBLE`
- heap/object classification: `HEAP_PAGE_TOUCH_GROWTH`
- HEAP_PAGE_TOUCH_GROWTH (HIGH): heap PSS delta +4084 KB; heap used stayed near flat; class histogram bytes stayed near flat
- CLASS_COUNT_SAVINGS (MEDIUM): class histogram class-count delta -5
- ANONYMOUS_RW_ALLOCATOR_REDUCTION (MEDIUM): anonymous_rw PSS median delta -2726 KB

Qualification is diagnostic only. All headline effects above come from six direct within-block comparisons; historical medians were not added.

Raw run-level evidence, local paths, private configuration, service source, credentials, runtime images, and CDS archives are excluded from Git.
