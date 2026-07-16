# Patient Final V1 -> V2 Verdict

Status: **FAIL_PATIENT_NOT_CONFIRMED**
Recommendation: **BLOCK_RUNTIME_PROMOTION**

The bounded root-cause investigation found and fixed a real artifact-scope defect: the first V2 artifact reduced `jmoa-runtime-lib`. The reducer now supports artifact exclusions, Patient excludes that support JAR, and the corrected V1/V2 runtime library is byte-identical. Phase 31 C2 was also confirmed as equivalent to the finalized Patient V1 optimizer policy.

## Corrected Artifact

| Item | Final V1 | Corrected final V2 | Delta |
|---|---:|---:|---:|
| Outer application JAR bytes | 99,585,809 | 95,770,204 | -3,815,605 |
| Embedded dependency bytes | 98,600,337 | 94,848,125 | -3,752,212 |
| Embedded dependency JARs | 162 | 162 | 0 |
| Reduced classes | n/a | 32,616 | n/a |

Application classes and the Spring Boot loader are unchanged by content. Raw class auditing found zero non-target byte differences. Repacking introduces container-level ZIP metadata differences, so the comparator classification is `PACKAGING_ONLY_DIFFERENCES_PRESENT`, not a claim of byte-identical outer JARs.

## Diagnosis

The original controlled `+10,504 KB` anonymous writable PSS movement consisted of `+8,932 KB` outside the Java heap and `+1,572 KB` in Java heap pages. Histogram bytes and heap occupancy did not grow, so retained application objects do not explain the regression.

| Diagnostic pair | PSS KB | Private Dirty KB | memory.current B |
|---|---:|---:|---:|
| No CDS | -10,455 | -10,612 | -11,104,256 |
| Deterministic separate CDS archives | -5,366 | -5,488 | -8,081,408 |
| Normal state before forced GC | +3,651 | +3,768 | +1,134,592 |
| Post-forced-GC, diagnostic only | +3,817 | +3,928 | +1,241,088 |
| Settle T+20 | +6,350 | +6,348 | +3,665,920 |
| Settle T+60 | +6,266 | +6,244 | +3,629,056 |
| Settle T+120 | +6,270 | +6,244 | +3,719,168 |
| Reversed-order CDS | -858 | -960 | -3,600,384 |

The result is classified `CDS_INTERACTION`, with `HEAP_PAGE_TOUCH_VARIANCE` as the secondary mechanism. No-CDS is strongly favorable, while fresh CDS runs vary in sign; forced GC and longer settling do not remove that variance.

## Final Balanced Confirmation

Protocol: corrected artifacts, separate deterministic CDS archives, Spring Boot fat JAR, Java 26, no runtime javaagent, cache reset before every run, T+20 post-workload capture, and order `B->C`, `C->B`, `B->C`.

| Metric | Result | Required |
|---|---:|---:|
| Valid runs | 6/6 | 6/6 |
| Paired wins | 1/3 | >= 2/3 |
| Pair PSS deltas | +668, +2,870, -2,373 KB | n/a |
| Median PSS delta | +668 KB | <= -4,096 KB |
| Median Private_Dirty delta | +736 KB | <= -1,024 KB |
| Median memory.current delta | -1,945,600 B | <= -1,048,576 B |
| Workload errors | 0 | 0 |
| V2-C | MIXED_METRICS_NEEDS_RERUN | CONFIRMED_WIN |
| V2-D | `ANONYMOUS_RW_ALLOCATOR_GROWTH` | required |

Patient remains artifact-safe but is not admitted for runtime promotion. The aggregate three-service matrix stays blocked. RC2's narrower PetClinic claim remains valid and is not retagged or deleted.

Raw Patient runs, private configuration, archives, and local paths remain outside the public repository.
