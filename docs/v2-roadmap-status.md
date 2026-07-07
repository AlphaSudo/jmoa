# JMOA V2 Roadmap Status

Status: V2-A through V2-I are closed or confirmed as the current V2 foundation.

This document records the public roadmap boundary after
`v0.7.3-v2g-artifact-generalization`, the V2-H hardened reducer screen, and the
V2-I raw reducer recovery confirmation.

## Closed Milestones

| Milestone | Status | Closed As |
| --- | --- | --- |
| V2-A | Closed | Generated/synthetic/proxy/AOT visibility, runtime attribution, safety taxonomy, and ROI infrastructure |
| V2-B | Closed | Bytecode/classfile footprint profiler, near-64KB risk reporting, attribute/constant-pool reporting, and runtime correlation |
| V2-C | Closed | Evidence truth engine with validation, paired confirmation, variance classification, perturbation detection, and historical replay proof |
| V2-D | Closed | Memory attribution engine with category deltas, smaps/NMT reconciliation, heap/object attribution, class/metaspace attribution, and historical attribution proof |
| V2-E | Closed | Opt-in LVT/LVTT artifact reducer for dependency jars, with PetClinic artifact smoke, semantic smoke, V2-C confirmation, and V2-D attribution |
| V2-F | Closed | Reducer productization with signed/MR/sealed JAR safety, reducer manifest hashes, PetClinic hardened artifact smoke, Doctor artifact smoke, and admission policy |
| V2-G | Closed | Doctor corrected D2 artifact-level reducer generalization and materialization proof, with runtime smoke blocked |
| V2-H | Screened | Productized V2-F-hardened PetClinic reducer screen; artifact gate passed, runtime promotion failed |
| V2-I | Closed | Reducer policy-diff recovery with an explicit raw engine and V2-C-confirmed PetClinic runtime win |

## Current Foundation

```text
V2-A = what generated/synthetic shapes exist?
V2-B = what bytecode/classfile footprint exists?
V2-C = can we trust the measurement?
V2-D = why did memory move?
V2-E = can the first safe artifact reducer pass controlled gates?
V2-F = can that reducer be made safer and auditable for real dependency surfaces?
V2-G = does the hardened reducer generalize to a second service at artifact level?
V2-H = does the hardened reducer retain the PetClinic runtime win?
V2-I = can a narrower raw engine recover runtime-positive behavior while preserving V2-F jar safety?
```

Together, these milestones provide visibility, validation, explanation, the
first controlled post-v1 reducer, reducer productization, and a clear claim
boundary between the earlier runtime-confirmed V2-E reducer, the safer
V2-F-hardened reducer, and the V2-I raw recovery engine. Reducer behavior is
still disabled by default and report-only by default unless explicit
release-low-footprint reducer flags are enabled.

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

This screen has now been superseded by the 3-pair V2-E confirmation below.

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

## V2-F Productization

V2-F is closed as reducer productization:

```text
signed / multi-release / sealed JAR safety
reducer manifest with input/output hashes
PetClinic hardened artifact smoke
Doctor artifact-level smoke
reducer admission policy
```

V2-F does not add a new runtime performance claim.

## V2-G Artifact Generalization

V2-G selected Doctor corrected D2 as the second-service target and completed
artifact-level generalization:

```text
dependency-jar bytes removed: 4,156,014
manifest artifacts with hashes: 184/184
BOOT-INF/lib entries replaced in materialized fat JAR: 184
```

Doctor semantic smoke and runtime memory screening are blocked until the Doctor
runtime image stack is rebuilt or provided and the CDS policy for the reduced
artifact is decided. V2-G does not add a cross-service runtime claim.

## V2-H Hardened Reducer Screen

V2-H reconciled the earlier V2-E runtime-confirmed reducer with the V2-F
hardened/productized reducer policy. The `v0.7.0-v2e-runtime-confirmed` result
used an earlier reducer artifact set:

```text
artifact byte delta: -5,395,897
median PSS delta: -1,624 KB
paired wins: 2/3
```

The V2-F-hardened reducer skips signed, multi-release, sealed, and
BootstrapMethods-bearing surfaces. The hardened PetClinic artifact is smaller,
but removes fewer total bytes:

```text
materialized dependency jar byte delta: -3,855,370
BOOT-INF/lib entries replaced: 162/162
```

The V2-H single-screen runtime gate did not pass:

```text
PSS delta: +7,804 KB
Private_Dirty delta: +7,824 KB
memory.current delta: -7,364,608 bytes
workload errors: 0
```

Because PSS and Private_Dirty regressed by more than 1 MB, V2-H did not proceed
to 3-pair confirmation. The productized hardened reducer is not runtime
confirmed.

## V2-I Raw Reducer Recovery

V2-I analyzed the policy difference between V2-E and V2-H and added an explicit
raw reducer engine:

```text
jmoa.reducer.engine=raw
```

The raw engine preserves BootstrapMethods while removing only nested
LocalVariableTable and LocalVariableTypeTable attributes. It keeps the V2-F jar
safety policy:

```text
signed jars skipped
multi-release jars skipped
sealed jars skipped
module-info.class preserved
```

Artifact diff:

```text
V2-H hardened materialized jar delta: -3,855,370 bytes
V2-I raw materialized jar delta: -3,668,109 bytes
V2-H BootstrapMethods classes skipped: 6,029
V2-I BootstrapMethods classes skipped: 0
```

V2-I runtime confirmation:

```text
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
paired wins: 2/3
median PSS delta: -4,467 KB
median Private_Dirty delta: -4,208 KB
median memory.current delta: -4,493,312 bytes
```

V2-D attribution:

```text
primary: HEAP_PAGE_TOUCH_REDUCTION
secondary: ANONYMOUS_RW_ALLOCATOR_REDUCTION
not retained-object shrinkage or class-count savings alone
```

## Next Gate

The next gate is raw-engine productization and portability:

```text
document raw engine usage and safety boundaries
avoid transferring the PetClinic claim to Doctor/fat-JAR/CDS modes
run second-service semantic smoke or Doctor runtime unblock only with fresh policy-specific artifacts
```

## V2-E Boundary

V2-E does not reopen generated-class mutation. It also does not implement
large-method splitting, constant-pool rewriting, BootstrapMethods rewriting,
annotation stripping, StackMapTable stripping, or LineNumberTable stripping.
Classes with `BootstrapMethods` are skipped by the first mutation prototype.

V2-E has a confirmed PetClinic runtime claim only for the documented
EXPLODED_BOOT_APP no-CDS protocol and the earlier V2-E reducer policy. That
runtime claim is not transferred to the V2-F-hardened/productized reducer.

V2-I has a separate confirmed PetClinic runtime claim for the explicit raw
engine under the same EXPLODED_BOOT_APP no-CDS protocol. That claim is not
transferred to Doctor, fat-JAR mode, CDS/AppCDS mode, startup performance, or
cross-service generalization. Any broader runtime performance claim requires
fresh V2-C validation and V2-D attribution.
