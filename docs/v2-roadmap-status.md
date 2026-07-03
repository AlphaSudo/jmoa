# JMOA V2 Roadmap Status

Status: V2-A through V2-E foundation complete through the first artifact
reducer prototype and PetClinic runtime screen.

This document records the public roadmap boundary after
`v0.6.0-v2e-debug-metadata-reducer`.

## Closed Milestones

| Milestone | Status | Closed As |
| --- | --- | --- |
| V2-A | Closed | Generated/synthetic/proxy/AOT visibility, runtime attribution, safety taxonomy, and ROI infrastructure |
| V2-B | Closed | Bytecode/classfile footprint profiler, near-64KB risk reporting, attribute/constant-pool reporting, and runtime correlation |
| V2-C | Closed | Evidence truth engine with validation, paired confirmation, variance classification, perturbation detection, and historical replay proof |
| V2-D | Closed | Memory attribution engine with category deltas, smaps/NMT reconciliation, heap/object attribution, class/metaspace attribution, and historical attribution proof |
| V2-E | Closed as prototype | Opt-in debug metadata artifact reducer for dependency jars, with PetClinic artifact smoke, semantic smoke, and single-screen runtime promotion gate |

## Current Foundation

```text
V2-A = what generated/synthetic shapes exist?
V2-B = what bytecode/classfile footprint exists?
V2-C = can we trust the measurement?
V2-D = why did memory move?
V2-E = can the first safe artifact reducer pass controlled gates?
```

Together, these milestones provide visibility, validation, explanation, and the
first controlled post-v1 artifact reducer. V2-E is still disabled by default and
report-only by default.

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

V2-E confirmed performance claim remains blocked:

```text
runtime memory claim not made
startup claim not made
3-pair P2 vs P2+Reducer confirmation not run
V2-C/V2-D performance gate not passed
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

## Next Gate

The next gate is:

```text
P2-1  -> P2R-1
P2-2  -> P2R-2
P2-3  -> P2R-3
```

using V2-C validation and V2-D attribution.

## V2-E Boundary

V2-E does not reopen generated-class mutation. It also does not implement
large-method splitting, constant-pool rewriting, BootstrapMethods rewriting,
annotation stripping, StackMapTable stripping, or LineNumberTable stripping.
Classes with `BootstrapMethods` are skipped by the first mutation prototype.

V2-E has only an artifact-footprint claim so far. Any runtime performance claim
requires V2-C validation and V2-D attribution.
