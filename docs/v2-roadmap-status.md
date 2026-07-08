# JMOA V2 Roadmap Status

Status: V2-A through V2-J are closed or confirmed as the current V2 foundation.

This document records the public roadmap boundary after
`v0.7.3-v2g-artifact-generalization`, the V2-H hardened reducer screen, the
V2-I raw reducer recovery confirmation, and the V2-J raw engine productization
work.

## Closure Taxonomy

V2 phase status uses explicit closure types. See:

- [V2 Phase Closure Taxonomy](v2-phase-closure-taxonomy.md)

Report-only and artifact-only phases are considered closed only within their
declared closure type. They are not silently promoted into runtime or optimizer
claims.

## Closed Milestones

| Milestone | Closure Type | Closed As |
| --- | --- | --- |
| V2-A | `CLOSED_INFRASTRUCTURE` | Generated/synthetic/proxy/AOT visibility, runtime attribution, safety taxonomy, and ROI infrastructure |
| V2-B | `CLOSED_INFRASTRUCTURE` | Bytecode/classfile footprint profiler, near-64KB risk reporting, attribute/constant-pool reporting, and runtime correlation |
| V2-C | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Evidence truth engine with validation, paired confirmation, variance classification, perturbation detection, and historical replay proof |
| V2-D | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Memory attribution engine with category deltas, smaps/NMT reconciliation, heap/object attribution, class/metaspace attribution, and historical attribution proof |
| V2-E | `CLOSED_CONFIRMED` | Opt-in LVT/LVTT artifact reducer for dependency jars, with PetClinic artifact smoke, semantic smoke, V2-C confirmation, and V2-D attribution |
| V2-F | `CLOSED_ARTIFACT_ONLY` | Reducer productization with signed/MR/sealed JAR safety, reducer manifest hashes, PetClinic hardened artifact smoke, Doctor artifact smoke, and admission policy |
| V2-G | `CLOSED_ARTIFACT_ONLY` | Doctor corrected D2 artifact-level reducer generalization and materialization proof, with runtime smoke blocked |
| V2-H | `SCREEN_FAILED` | Productized V2-F-hardened PetClinic reducer screen; artifact gate passed, runtime promotion failed |
| V2-I | `CLOSED_CONFIRMED` | Reducer policy-diff recovery with an explicit raw engine and V2-C-confirmed PetClinic runtime win |
| V2-J | `CLOSED_ARTIFACT_ONLY` | Raw engine productization with byte-preservation auditor, manifest v2, verifier tests, Doctor raw artifact smoke, and runtime unblock plan |
| V2-K | `OPEN_BACKLOG` | Public second runtime target has been selected but not yet screened |

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
V2-J = can the raw engine be made byte-auditable and portable at artifact level?
```

Together, these milestones provide visibility, validation, explanation, the
first controlled post-v1 reducer, reducer productization, and a clear claim
boundary between the earlier runtime-confirmed V2-E reducer, the safer
V2-F-hardened reducer, the V2-I raw recovery engine, and the V2-J productized
raw engine. Reducer behavior is still disabled by default and report-only by
default unless explicit release-low-footprint reducer flags are enabled.

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

## V2-J Raw Engine Productization

V2-J adds byte-level product hardening around the raw reducer:

```text
raw byte-preservation auditor
jmoa-reducer-manifest-v2.json
raw-reducer-byte-preservation-report.json/md
stronger raw verifier tests
Doctor corrected D2 raw artifact smoke
Doctor runtime unblock plan
public second runtime target selection
```

The raw auditor independently normalizes original and reduced class bytes by
removing only `LocalVariableTable` and `LocalVariableTypeTable`. The normalized
byte streams must match exactly.

Doctor corrected D2 raw artifact smoke:

```text
dependency jars processed: 184
static classes scanned: 58,924
classes reduced and audited: 31,942
failed audits: 0
BootstrapMethods classes skipped: 0
compressed dependency-jar bytes removed: 3,926,870
```

This is still artifact-level only. Doctor semantic smoke and runtime measurement
remain blocked until the private runtime stack and CDS/no-CDS policy are settled.

## Next Gate

The next gate is public second-runtime portability:

```text
select a public second Spring Boot runtime target
run semantic smoke
run single-screen raw reducer runtime check
promote to V2-C confirmation only if the screen passes
```

Recommended public second runtime target:

```text
Spring PetClinic visits-service
```

See:

- [V2-K Target Selection](v2-k/v2k-target-selection.md)

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

V2-J productizes the raw engine and proves artifact-level portability on Doctor
corrected D2 dependency libs with byte-preservation auditing. It does not add a
new runtime memory claim.
