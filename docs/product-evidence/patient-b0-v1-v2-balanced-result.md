# patient-service Direct B0/V1/V2 Result

- Terminal outcome: **B0_TO_V1_NOT_REPRODUCED**
- Protocol: `PATIENT_B0_V1_V2_BALANCED_V1`
- Runtime: `SPRING_BOOT_FAT_JAR` / `JDK_BASE_CDS_LOW_DIRTY`
- Final observations: 18/18 valid; semantic errors: 0

| Comparison | PSS median | PSS wins | PSS 95% CI | Private Dirty median | memory.current median | V2-C | V2-D |
|---|---:|---:|---:|---:|---:|---|---|
| B0_TO_V1 | 910 KB | 3/6 | [-5326.5, 6151] KB | 844 KB | 827392 B | MIXED_METRICS_NEEDS_RERUN | True |
| V1_TO_V2 | -6135.5 KB | 5/6 | [-7925.5, 2218] KB | -6070 KB | -6387712 B | CONFIRMED_WIN | True |
| B0_TO_V2 | -1266.5 KB | 4/6 | [-12330.5, 3488.5] KB | -1238 KB | -1103872 B | CONFIRMED_WIN | True |

## B0 To V2 Blocks

| Block | Order | PSS | Private Dirty | memory.current |
|---:|---|---:|---:|---:|
| 1 | B0,V1,V2 | -13735 KB | -13808 KB | -13897728 B |
| 2 | B0,V2,V1 | -201 KB | -392 KB | -208896 B |
| 3 | V1,B0,V2 | 5328 KB | 5384 KB | 5390336 B |
| 4 | V1,V2,B0 | -2332 KB | -2084 KB | -1998848 B |
| 5 | V2,B0,V1 | -10926 KB | -10940 KB | -11071488 B |
| 6 | V2,V1,B0 | 1649 KB | 1632 KB | 1757184 B |

## Attribution

- smaps/NMT reconciliation: `NMT_VISIBLE`
- heap/object classification: `HEAP_PAGE_TOUCH_GROWTH`
- HEAP_PAGE_TOUCH_GROWTH (HIGH): heap PSS delta +1254 KB; heap used stayed near flat; class histogram bytes stayed near flat
- CLASS_COUNT_SAVINGS (MEDIUM): class histogram class-count delta -5
- ANONYMOUS_RW_ALLOCATOR_REDUCTION (MEDIUM): anonymous_rw PSS median delta -2624 KB

Qualification is diagnostic only. All headline effects above come from six direct within-block comparisons; historical medians were not added.

Raw run-level evidence, local paths, private configuration, service source, credentials, runtime images, and CDS archives are excluded from Git.
