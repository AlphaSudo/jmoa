# V2-K Doctor V2-D Attribution

Status:

```text
PASSED
```

V2-D analyzed the V2-C-valid Doctor confirmation evidence and attributed the
runtime memory movement.

Primary category deltas:

| Category | Median delta |
| --- | ---: |
| TOTAL_PSS | `-5,156 KB` |
| PRIVATE_DIRTY | `-5,212 KB` |
| CGROUP_MEMORY_CURRENT | `-6,975,488 bytes` |
| HEAP_PSS | `0 KB` |
| HEAP_USED | `+190 KB` |
| CLASS_HISTOGRAM_BYTES | `-2,024 bytes` |
| ANONYMOUS_RW_PSS | `-3,460 KB` |
| MAPPED_FILE_PSS | `-1,642 KB` |
| NMT_TOTAL_COMMITTED | `-2,534 KB` |
| NMT_METASPACE_COMMITTED | `-843 KB` |
| NMT_CLASS_COMMITTED | `-262 KB` |

smaps/NMT reconciliation:

```text
classification: NMT_INVISIBLE_OR_PARTIAL
NMT-to-PSS gap: -2,622 KB
```

Causal hypotheses:

```text
CLASS_COUNT_SAVINGS: MEDIUM
ANONYMOUS_RW_ALLOCATOR_REDUCTION: MEDIUM
```

Interpretation:

```text
The Doctor D2R win is not a heap-retained-object reduction. Heap PSS stayed flat,
heap used moved only slightly, and class histogram bytes were effectively flat.
The main measured movement is anonymous_rw PSS reduction, mapped-file PSS
reduction, and NMT-visible class/metaspace reduction, with class-count savings as
supporting evidence rather than sole causality.
```

Boundary:

```text
V2-D is explanatory. It does not enable new reducer types, generated-class
mutation, or broad fat-JAR/CDS claims by itself.
```
