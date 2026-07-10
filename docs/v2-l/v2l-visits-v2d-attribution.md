# V2-L Visits V2-D Attribution

Status:

```text
PASSED
```

V2-D analyzed the V2-C-valid visits evidence.

Primary category deltas:

| Category | Median delta |
| --- | ---: |
| TOTAL_PSS | `-2,012 KB` |
| PRIVATE_DIRTY | `-1,680 KB` |
| CGROUP_MEMORY_CURRENT | `-1,712,128 bytes` |
| ANONYMOUS_RW_PSS | `-5,264 KB` |
| NMT_TOTAL_COMMITTED | `-1,259 KB` |
| NMT_METASPACE_COMMITTED | `-2,814 KB` |
| NMT_CLASS_COMMITTED | `-29 KB` |
| MAPPED_FILE_PSS | `-80 KB` |
| HEAP_PSS | `+1,988 KB` |
| HEAP_USED | `-1,631 KB` |
| CLASS_HISTOGRAM_BYTES | `-46,656 bytes` |

smaps/NMT reconciliation:

```text
classification: NMT_VISIBLE
NMT-to-PSS gap: -753 KB
```

V2-D ranked one causal hypothesis:

```text
ANONYMOUS_RW_ALLOCATOR_REDUCTION: MEDIUM
```

Interpretation:

```text
The confirmed total-memory win is dominated by lower anonymous_rw PSS and
NMT-visible metaspace movement under a fixed MALLOC_ARENA_MAX=1 policy.
Heap PSS increased, class-histogram class count was neutral, and histogram
bytes moved only slightly. The result must not be described as retained-object
or class-count reduction.
```

The hypothesis names the observed memory category; it does not claim that V2-L
changed allocator policy. Both variants used the same allocator policy.
