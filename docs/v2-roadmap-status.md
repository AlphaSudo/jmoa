# JMOA V2 Roadmap Status

Status: V2-A through V2-D report-only foundation complete.

This document records the public roadmap boundary after
`v0.5.0-v2d-memory-attribution`.

## Closed Milestones

| Milestone | Status | Closed As |
| --- | --- | --- |
| V2-A | Closed | Generated/synthetic/proxy/AOT visibility, runtime attribution, safety taxonomy, and ROI infrastructure |
| V2-B | Closed | Bytecode/classfile footprint profiler, near-64KB risk reporting, attribute/constant-pool reporting, and runtime correlation |
| V2-C | Closed | Evidence truth engine with validation, paired confirmation, variance classification, perturbation detection, and historical replay proof |
| V2-D | Closed | Memory attribution engine with category deltas, smaps/NMT reconciliation, heap/object attribution, class/metaspace attribution, and historical attribution proof |

## Current Foundation

```text
V2-A = what generated/synthetic shapes exist?
V2-B = what bytecode/classfile footprint exists?
V2-C = can we trust the measurement?
V2-D = why did memory move?
```

Together, these milestones provide visibility, validation, and explanation. They
do not enable new post-v1 bytecode reducers by default.

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
debug attribute stripping not enabled
LocalVariableTable reducer not enabled
large-method splitting not implemented
constant-pool reducer not implemented
BootstrapMethods reducer not implemented
no bytecode-size memory or startup win claim
```

## Next Allowed Phase

The next phase starts the first controlled reducer prototype:

```text
V2-E = opt-in release-low-footprint reducer for LocalVariableTable and
       LocalVariableTypeTable only.
```

V2-E must stay disabled by default, report-only by default, and performance
claims must pass V2-C validation plus V2-D attribution.

## V2-E Boundary

V2-E does not reopen generated-class mutation. It also does not implement
large-method splitting, constant-pool rewriting, BootstrapMethods rewriting,
annotation stripping, StackMapTable stripping, or LineNumberTable stripping.
Classes with `BootstrapMethods` are skipped by the first mutation prototype.
