# Doctor Reconstructed B0/V1 Attribution

- Status: **DIAGNOSTIC_EXISTING_PAIR**
- Comparison: **V1 second - B0 first**
- Semantic gate: **SEMANTICALLY_EQUIVALENT_VOLATILE_FIELDS**
- PSS: **2661 KB**
- Private Dirty: **2580 KB**
- memory.current: **2789376 bytes**
- NMT committed: **-366 KB**
- Reconciliation: **NMT_PARTIAL_ANONYMOUS_RW_OUTSIDE_HEAP**
- Primary mapping movement: **ANONYMOUS_RW_OUTSIDE_HEAP**

| Mapping category | B0 PSS KB | V1 PSS KB | Delta PSS KB | Delta Private Dirty KB | Delta Anonymous KB |
|---|---:|---:|---:|---:|---:|
| ANONYMOUS_EXECUTABLE | 16228 | 16076 | -152 | -152 | -152 |
| ANONYMOUS_OTHER | 4 | 4 | 0 | 0 | 0 |
| ANONYMOUS_RW_OUTSIDE_HEAP | 203304 | 206248 | 2944 | 2944 | 2944 |
| JAVA_HEAP | 63892 | 63684 | -208 | -208 | -208 |
| JDK_CDS_IMAGE | 12686 | 12686 | 0 | 0 | 0 |
| JDK_IMAGE | 1101 | 1101 | 0 | 0 | 0 |
| LIBJVM | 7221 | 7263 | 42 | 0 | 0 |
| MAPPED_FILE_OTHER | 180 | 199 | 19 | -4 | 0 |
| NATIVE_LIBRARY | 1071 | 1092 | 21 | 0 | 0 |
| SPECIAL_MAPPING | 0 | 0 | 0 | 0 | 0 |
| SPECIAL_OR_OTHER | 108 | 108 | 0 | 0 | 0 |
| THREAD_STACK | 32 | 32 | 0 | 0 | 0 |

## JVM

- Heap PSS delta: **-208 KB**
- Heap used delta: **-9719 KB**
- Heap committed delta: **-452 KB**
- Loaded classes delta: **-116**
- Hidden classes delta: **16**
- Class loaders delta: **16**
- Metaspace committed delta: **-64 KB**
- Threads delta: **0**
- Histogram bytes delta: **134320**

## Timing

- B0 JVM age at capture: **81.338 s**
- V1 JVM age at capture: **78.385 s**
- V1 - B0 age: **-2.953 s**
- Classification: **TIMING_CONFOUNDED**

GC count, safepoint count, and exact compilation count were not captured. NMT
Compiler and Code categories are reported in JSON without pretending they are
event counters.

## Claim Boundary

This is one B0-first/V1-second diagnostic in a reconstructed support environment.
It does not prove a V1 regression. The result remains
DOCTOR_HISTORICAL_V1_DIRECTION_NOT_REPRODUCED_IN_SINGLE_ORDER until the one
reversed diagnostic is classified.
