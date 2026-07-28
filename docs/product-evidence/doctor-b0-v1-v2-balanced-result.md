# doctor-service Direct B0/V1/V2 Result

- Terminal outcome: **COMPLETE_PRODUCT_WIN**
- Protocol: `DOCTOR_B0_V1_V2_BALANCED_V1`
- Runtime: `SPRING_BOOT_FAT_JAR` / `APPLICATION_CDS`
- Final observations: 18/18 valid; semantic errors: 0

| Comparison | PSS median | PSS wins | PSS 95% CI | Private Dirty median | memory.current median | V2-C | V2-D |
|---|---:|---:|---:|---:|---:|---|---|
| B0_TO_V1 | 654 KB | 3/6 | [-2571.5, 5995] KB | 498 KB | 1439744 B | MIXED_METRICS_NEEDS_RERUN | True |
| V1_TO_V2 | -6261.5 KB | 6/6 | [-7644, -2263] KB | -6108 KB | -13082624 B | CONFIRMED_WIN | True |
| B0_TO_V2 | -4715.5 KB | 5/6 | [-7107, -268.5] KB | -4904 KB | -10874880 B | CONFIRMED_WIN | True |

## B0 To V2 Blocks

| Block | Order | PSS | Private Dirty | memory.current |
|---:|---|---:|---:|---:|
| 1 | B0,V1,V2 | -7104 KB | -7136 KB | -13348864 B |
| 2 | B0,V2,V1 | -5965 KB | -6060 KB | -12029952 B |
| 3 | V1,B0,V2 | -7110 KB | -7256 KB | -13074432 B |
| 4 | V1,V2,B0 | 2876 KB | 2976 KB | -3158016 B |
| 5 | V2,B0,V1 | -3413 KB | -3548 KB | -9564160 B |
| 6 | V2,V1,B0 | -3466 KB | -3748 KB | -9719808 B |

## Attribution

- smaps/NMT reconciliation: `NMT_VISIBLE`
- heap/object classification: `UNKNOWN`
- CLASS_COUNT_SAVINGS (MEDIUM): class histogram class-count delta -131
- ANONYMOUS_RW_ALLOCATOR_REDUCTION (MEDIUM): anonymous_rw PSS median delta -2610 KB

Qualification is diagnostic only. All headline effects above come from six direct within-block comparisons; historical medians were not added.

Raw run-level evidence, local paths, private configuration, service source, credentials, runtime images, and CDS archives are excluded from Git.
