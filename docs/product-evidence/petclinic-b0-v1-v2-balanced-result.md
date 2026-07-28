# spring-petclinic-customers-service Direct B0/V1/V2 Result

- Terminal outcome: **B0_TO_V1_NOT_REPRODUCED**
- Protocol: `PETCLINIC_CUSTOMERS_B0_V1_V2_BALANCED_V1`
- Runtime: `EXPLODED_BOOT_APP` / `NO_CDS_LOW_DIRTY`
- Final observations: 18/18 valid; semantic errors: 0

| Comparison | PSS median | PSS wins | PSS 95% CI | Private Dirty median | memory.current median | V2-C | V2-D |
|---|---:|---:|---:|---:|---:|---|---|
| B0_TO_V1 | 2516 KB | 3/6 | [-3508, 12539] KB | 2704 KB | 2658304 B | MIXED_METRICS_NEEDS_RERUN | True |
| V1_TO_V2 | -3736 KB | 5/6 | [-14922, 1862.5] KB | -3926 KB | -3887104 B | CONFIRMED_WIN | True |
| B0_TO_V2 | -2947 KB | 4/6 | [-6516.5, 4215] KB | -2722 KB | -2703360 B | CONFIRMED_WIN | True |

## B0 To V2 Blocks

| Block | Order | PSS | Private Dirty | memory.current |
|---:|---|---:|---:|---:|
| 1 | B0,V1,V2 | 6935 KB | 6704 KB | 7098368 B |
| 2 | B0,V2,V1 | -2271 KB | -1944 KB | -1712128 B |
| 3 | V1,B0,V2 | -8171 KB | -8064 KB | -8163328 B |
| 4 | V1,V2,B0 | -4862 KB | -4680 KB | -4710400 B |
| 5 | V2,B0,V1 | 1495 KB | 1268 KB | 1011712 B |
| 6 | V2,V1,B0 | -3623 KB | -3500 KB | -3694592 B |

## Attribution

- smaps/NMT reconciliation: `NMT_INVISIBLE_OR_PARTIAL`
- heap/object classification: `UNKNOWN`
- CLASS_COUNT_SAVINGS (MEDIUM): class histogram class-count delta -175
- ANONYMOUS_RW_ALLOCATOR_REDUCTION (MEDIUM): anonymous_rw PSS median delta -2054 KB

Qualification is diagnostic only. All headline effects above come from six direct within-block comparisons; historical medians were not added.

Raw run-level evidence, local paths, private configuration, service source, credentials, runtime images, and CDS archives are excluded from Git.
