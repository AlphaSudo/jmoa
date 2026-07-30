# Doctor Reconstructed B0/V1 Attribution

- Status: **DIAGNOSTIC_EXISTING_PAIR**
- Comparison: **V1_FIRST_MINUS_B0_SECOND**
- Semantic gate: **SEMANTICALLY_EQUIVALENT_VOLATILE_FIELDS**
- PSS: **8141 KB**
- Private Dirty: **8260 KB**
- memory.current: **8454144 bytes**
- NMT committed: **-1080 KB**
- Reconciliation: **NMT_PARTIAL_ANONYMOUS_RW_OUTSIDE_HEAP**
- Primary mapping movement: **ANONYMOUS_RW_OUTSIDE_HEAP**

| Mapping category | B0 PSS KB | V1 PSS KB | Delta PSS KB | Delta Private Dirty KB | Delta Anonymous KB |
|---|---:|---:|---:|---:|---:|
| ANONYMOUS_EXECUTABLE | 16184 | 16092 | -92 | -92 | -92 |
| ANONYMOUS_OTHER | 4 | 4 | 0 | 0 | 0 |
| ANONYMOUS_RW_OUTSIDE_HEAP | 198860 | 207344 | 8484 | 8484 | 8484 |
| JAVA_HEAP | 63988 | 63868 | -120 | -120 | -120 |
| JDK_CDS_IMAGE | 12686 | 12686 | 0 | 0 | 0 |
| JDK_IMAGE | 1101 | 1101 | 0 | 0 | 0 |
| LIBJVM | 7225 | 7136 | -89 | 0 | 0 |
| MAPPED_FILE_OTHER | 154 | 208 | 54 | -12 | 0 |
| NATIVE_LIBRARY | 1058 | 1020 | -38 | 0 | 0 |
| SPECIAL_MAPPING | 0 | 0 | 0 | 0 | 0 |
| SPECIAL_OR_OTHER | 108 | 108 | 0 | 0 | 0 |
| THREAD_STACK | 32 | 32 | 0 | 0 | 0 |

## JVM

- Heap PSS delta: **-120 KB**
- Heap used delta: **-8310 KB**
- Heap committed delta: **24 KB**
- Loaded classes delta: **-268**
- Hidden classes delta: **9**
- Class loaders delta: **9**
- Metaspace committed delta: **-1280 KB**
- Threads delta: **0**
- Histogram bytes delta: **-257744**

## Timing

- B0 JVM age at capture: **69.924 s**
- V1 JVM age at capture: **86.22 s**
- V1 - B0 age: **16.296 s**
- Classification: **TIMING_CONFOUNDED**

GC count, safepoint count, and exact compilation count were not captured. NMT
Compiler and Code categories are reported in JSON without pretending they are
event counters.

## Claim Boundary

This is one B0-first/V1-second diagnostic in a reconstructed support environment.
It does not prove a V1 regression. The result remains
DOCTOR_HISTORICAL_V1_DIRECTION_NOT_REPRODUCED_IN_SINGLE_ORDER until the one
reversed diagnostic is classified.
