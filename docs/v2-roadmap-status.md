# JMOA V2 Roadmap Status

Status: V2-A through V2-E complete through the first confirmed runtime reducer.

This document records the public roadmap boundary after
`v0.7.0-v2e-runtime-confirmed`.

## Closed Milestones

| Milestone | Status | Closed As |
| --- | --- | --- |
| V2-A | Closed | Generated/synthetic/proxy/AOT visibility, runtime attribution, safety taxonomy, and ROI infrastructure |
| V2-B | Closed | Bytecode/classfile footprint profiler, near-64KB risk reporting, attribute/constant-pool reporting, and runtime correlation |
| V2-C | Closed | Evidence truth engine with validation, paired confirmation, variance classification, perturbation detection, and historical replay proof |
| V2-D | Closed | Memory attribution engine with category deltas, smaps/NMT reconciliation, heap/object attribution, class/metaspace attribution, and historical attribution proof |
| V2-E | Closed | Opt-in LVT/LVTT artifact reducer for dependency jars, with PetClinic artifact smoke, semantic smoke, V2-C confirmation, and V2-D attribution |

## Current Foundation

```text
V2-A = what generated/synthetic shapes exist?
V2-B = what bytecode/classfile footprint exists?
V2-C = can we trust the measurement?
V2-D = why did memory move?
V2-E = can the first safe artifact reducer pass controlled gates?
```

Together, these milestones provide visibility, validation, explanation, and the
first controlled post-v1 reducer. V2-E is still disabled by default and
report-only by default unless explicit release-low-footprint reducer flags are
enabled.

## Still Blocked

V2-A mutation remains blocked:

```text
generated-class mutation disabled
Spring AOT BeanDefinition helper rewrite not implemented
CGLIB/JDK proxy/ByteBuddy/Hibernate proxy rewriting not implemented
no generated-class memory win claim
```

V2-B mutation remains blocked:

```text
debug stripping beyond V2-E LVT/LVTT not enabled
large-method splitting not implemented
constant-pool reducer not implemented
BootstrapMethods reducer not implemented
no bytecode-size memory or startup win claim
```

V2-E broader performance claims remain blocked:

```text
PetClinic EXPLODED_BOOT_APP no-CDS runtime claim confirmed
startup claim not made
fat-JAR claim not made
CDS/AppCDS claim not made
cross-service generalization not made
```

## Latest V2-E Screen

The V2-E runtime screen has passed:

```text
full P2
vs
full P2 + V2-E reducer
```

The screen used the real PetClinic exploded Boot no-CDS deployment shape,
`NO_CDS_LOW_DIRTY`, `MALLOC_ARENA_MAX=1`, no runtime javaagent, and the corrected
27 endpoint x 3 round workload. It passed the promotion gate:

```text
artifact byte delta: -5,395,897
workload errors: 0
PSS delta: -17,880 KB
Private_Dirty delta: -18,068 KB
memory.current delta: -35,205,120 bytes
```

This is still a single-screen result. It promotes V2-E to 3-pair confirmation,
but it is not a confirmed runtime memory claim.

## V2-E Confirmation

The 3-pair confirmation passed:

```text
P2-1  -> P2R-1
P2-2  -> P2R-2
P2-3  -> P2R-3
```

Result:

```text
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
paired wins: 2/3
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
```

V2-D attribution:

```text
NMT/smaps reconciliation: NMT_VISIBLE
heap/object attribution: HEAP_PAGE_TOUCH_GROWTH
anonymous_rw PSS median delta: -2,456 KB
NMT total committed median delta: -2,074 KB
NMT metaspace committed median delta: -3,271 KB
```

The confirmed result should not be explained as retained-object or class-count
reduction.

## Next Gate

The next gate is V2-F:

```text
generalize V2-E to a second service or productize the release-low-footprint reducer profile
```

## V2-E Boundary

V2-E does not reopen generated-class mutation. It also does not implement
large-method splitting, constant-pool rewriting, BootstrapMethods rewriting,
annotation stripping, StackMapTable stripping, or LineNumberTable stripping.
Classes with `BootstrapMethods` are skipped by the first mutation prototype.

V2-E has a confirmed PetClinic runtime claim only for the documented
EXPLODED_BOOT_APP no-CDS protocol. Any broader runtime performance claim requires
fresh V2-C validation and V2-D attribution.
