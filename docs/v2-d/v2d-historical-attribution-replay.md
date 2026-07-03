# V2-D Historical Attribution Replay

Status: passed for recovered PetClinic raw evidence.

V2-D was run against the same recovered raw evidence archive used to close
V2-C. Raw evidence remains private and was not committed. The checked-in
results below are compact, sanitized summaries.

## Phase 33M: PetClinic Exploded Boot Win

```text
verdict source: V2-C
verdict: CONFIRMED_WIN
service: spring-petclinic-customers-service
phase: 33M
deployment: EXPLODED_BOOT_APP
runtime policy: NO_CDS_LOW_DIRTY
```

Median category deltas:

```text
TOTAL_PSS:              -4,758 KB
PRIVATE_DIRTY:          -4,904 KB
memory.current:         -4,849,664 bytes
HEAP_PSS:               -7,152 KB
HEAP_PRIVATE_DIRTY:     -7,152 KB
HEAP_USED:              -90 KB
CLASS_HISTOGRAM_BYTES:  -94,312 bytes
ANONYMOUS_RW_PSS:       +1,976 KB
NMT_TOTAL_COMMITTED:    +979 KB
```

Attribution:

```text
heap classification: HEAP_PAGE_TOUCH_REDUCTION
smaps/NMT: NMT_INVISIBLE_OR_PARTIAL
class histogram class-count delta: -154
near-64KB methods from V2-B correlation: 3
near-64KB loaded methods: 0
```

Primary hypothesis:

```text
HEAP_PAGE_TOUCH_REDUCTION, high confidence
```

Interpretation:

```text
The confirmed memory win is dominated by heap PSS/private-dirty reduction.
Heap used and class histogram bytes stayed nearly flat, so the win should not
be described as retained-object shrinkage. Class-count reduction is supporting
evidence, not the whole explanation.
```

## Phase 33K.7b: PetClinic Fat-JAR Regression

```text
verdict source: V2-C
verdict: CONFIRMED_REGRESSION
service: spring-petclinic-customers-service
phase: 33K.7b
deployment: SPRING_BOOT_FAT_JAR
runtime policy: NO_CDS_LOW_DIRTY
```

Median category deltas:

```text
TOTAL_PSS:              +8,895 KB
PRIVATE_DIRTY:          +9,072 KB
memory.current:         +9,392,128 bytes
HEAP_PSS:               +8,748 KB
HEAP_PRIVATE_DIRTY:     +8,748 KB
HEAP_USED:              -45 KB
CLASS_HISTOGRAM_BYTES:  -46,888 bytes
ANONYMOUS_RW_PSS:       -128 KB
NMT_TOTAL_COMMITTED:    +1,472 KB
```

Attribution:

```text
heap classification: HEAP_PAGE_TOUCH_GROWTH
smaps/NMT: NMT_INVISIBLE_OR_PARTIAL
class histogram class-count delta: -177
near-64KB methods from V2-B correlation: 3
near-64KB loaded methods: 0
```

Primary hypothesis:

```text
HEAP_PAGE_TOUCH_GROWTH, high confidence
```

Interpretation:

```text
The fat-JAR full-P2 regression is dominated by heap PSS/private-dirty growth
despite lower class count and flat retained-object evidence. This reproduces
the audited Phase 33K.7b conclusion: the failure was page-touch/residency
behavior, not retained heap-object growth.
```

## Closure Decision

V2-D explains both directions of the historical result:

```text
33M exploded Boot: win from heap page-touch reduction plus class-count support.
33K.7b fat JAR: regression from heap page-touch growth despite class-count savings.
```

This confirms that V2-D is useful as an attribution layer before V2-A or V2-B
mutation work resumes.
