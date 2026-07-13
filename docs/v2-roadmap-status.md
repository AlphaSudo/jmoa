# JMOA V2 Roadmap Status

Status: V2-A through V2-V are closed, confirmed, or explicitly bounded within
their declared closure types. V2-P adds a report-only end-to-end workflow
coordinator around the existing V2-M, V2-N, V2-O, V2-C, and V2-D gates; V2-Q
adds application-class admission with runtime promotion blocked by confirmation;
V2-R adds report-only application/generated ROI discovery; V2-S reconciles
generated-family static inventory with diagnostic runtime relevance evidence;
V2-T adds SHA-gated lifecycle reconciliation and records missing evidence as
partial infrastructure rather than a prototype admission; V2-U adds the matched
startup/warmup/workload capture campaign contract and records the currently
available evidence as still incomplete.

This document records the public roadmap boundary after
`v0.7.3-v2g-artifact-generalization`, the V2-H hardened reducer screen, the
V2-I raw reducer recovery confirmation, the V2-J raw engine productization
work, the V2-K Doctor inventory/unblock gate and runtime confirmation, the
V2-L public visits-service confirmation, the V2-M reducer admission engine,
the V2-N runtime-policy recommendation engine, the V2-O runtime-policy
automation helpers, the V2-P workflow spine, the V2-Q application admission
result, V2-R ROI discovery, V2-S generated-family runtime relevance, V2-T
generated-family matched-evidence completion, V2-U matched lifecycle
campaign infrastructure, and V2-V fresh matched-capture execution tooling.

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
| V2-K | `CLOSED_CONFIRMED_DOCTOR` | Doctor runtime recovery succeeded, D2/D2R semantic smoke passed, single-screen measurement promoted, and 3-pair V2-C/V2-D confirmation passed |
| V2-L | `CLOSED_CONFIRMED` | Public visits-service baseline vs raw-reduced baseline passed artifact, materialization, semantic, screen, 3-pair V2-C, and V2-D gates |
| V2-M | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Report-only raw reducer admission engine, exact-protocol scoping, Maven goal, and 14/14 historical replay proof |
| V2-N | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Report-only runtime-policy registry, CDS artifact/archive mismatch protection, Maven goal, and 7/7 historical replay proof |
| V2-O | `CLOSED_CONFIRMED_INFRASTRUCTURE` | SHA-backed runtime preflight, explicit CDS training record, materialization proof, semantic smoke, V2-C-native paired capture, and V2-C/V2-D wrapper |
| V2-P | `CLOSED_CONFIRMED_INFRASTRUCTURE` | Script-first golden workflow, state machine, normalized workflow report, historical replay, and claim-register consistency guard |
| V2-Q | `CONFIRMATION_FAILED` | Application-class raw metadata admission passed artifact/semantic gates, but paired confirmation blocked runtime promotion |
| V2-R | `CLOSED_INFRASTRUCTURE` | Application/generated ROI discovery, runtime-relevance taxonomy, candidate ranking, and report-only recommendation classifications |
| V2-S | `CLOSED_INFRASTRUCTURE` | Generated-family registry, static/runtime reconciliation, safety matrix, ROI scoring, and recommendation replay; no family mutation admitted |
| V2-T | `PARTIAL_INFRASTRUCTURE` | SHA-gated generated-family evidence join, exclusive census, lifecycle model, and recommendation ingestion; no complete matched capture or family admission |
| V2-U | `PARTIAL_INFRASTRUCTURE` | Matched generated-family lifecycle capture harness, full identity tuple enforcement, per-service evidence-gap reports, cross-service matrix, and no family admission |
| V2-V | `PARTIAL_INFRASTRUCTURE` | Fresh matched generated-family campaign execution: preflight, automatic stage attribution, bundle validation, lifecycle/ROI reports, and no family admission; fresh complete bundles remain outstanding |

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
V2-K = can the raw-reduced Doctor D2R artifact pass real private fat-JAR/CDS runtime confirmation?
V2-L = can the productized raw reducer confirm on a second public runtime target?
V2-M = when should JMOA recommend, screen, limit, or block the raw reducer?
V2-N = which exact runtime policy is confirmed, screen-required, diagnostic-only, or blocked?
V2-O = how do we prepare and capture evidence for that policy without bypassing V2-C/V2-D?
V2-P = how do we coordinate the allowed workflow without flattening gates or declaring claims?
V2-Q = can packaged application classes be admitted safely, and do they survive confirmation?
V2-V = can the V2-U harness execute and validate fresh matched generated-family bundles across customers, visits, and Doctor?
V2-R = where is the next meaningful application/generated ROI surface, before mutation?
V2-S = which generated family is actually runtime-relevant and safe enough to deserve a future prototype?
V2-T = do the static artifact and startup/warmup/workload diagnostics actually describe the same generated-family surface?
V2-U = can fresh matched lifecycle bundles satisfy that evidence contract across customers, visits, and Doctor?
```

Together, these milestones provide visibility, validation, explanation, the
first controlled post-v1 reducer, reducer productization, and a clear claim
boundary between the earlier runtime-confirmed V2-E reducer, the safer
V2-F-hardened reducer, the V2-I raw recovery engine, the V2-J productized raw
engine, the V2-K private Doctor runtime confirmation, the V2-L public
visits-service confirmation, the V2-M recommendation engine, and the V2-N
runtime-policy recommendation engine. Reducer
behavior is still disabled by default and report-only by default unless explicit
release-low-footprint reducer flags are enabled. V2-M never enables mutation.
V2-N never changes runtime configuration, trains CDS archives, or reuses an
archive across artifact variants.
V2-O does not select runtime policy, mutate artifacts, or create performance
claims; it automates the prerequisite evidence workflow only.
V2-P does not add a reducer, select policy, train CDS implicitly, or create a
runtime claim. It records the ordered workflow and makes claim eligibility
explicit for review.
V2-Q does not create an application-class runtime claim. Its paired
confirmation failed.
V2-R does not enable application/generated mutation. It ranks future surfaces
and extends recommendation classifications only.
V2-S does not enable generated-class mutation or add a runtime claim. Its
diagnostic evidence is intentionally separate from V2-C memory confirmation.
V2-T does not enable generated-class mutation or add a runtime claim. A missing
or mismatched artifact fingerprint is an explicit non-admission result, not a
fallback to historical summaries.
V2-U does not enable generated-class mutation or add a runtime claim. It adds
the capture harness and strict identity tuple, then closes the available
customers/visits/Doctor evidence as still incomplete rather than admitting a
prototype from unmatched captures.

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
V2-E claim not transferred beyond customers-service
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

Doctor semantic smoke and runtime memory screening were blocked at V2-G time
until the Doctor runtime image stack was rebuilt and the CDS policy for the
reduced artifact was decided. V2-G itself does not add a cross-service runtime
claim; V2-K supersedes the blocker with a private Doctor runtime confirmation.

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

This artifact-level result was followed by the V2-K Doctor runtime recovery and
confirmation below.

## V2-K Doctor Runtime Confirmation

The Doctor runtime recovery blocker has been superseded. The missing pieces were
located or rebuilt locally:

```text
private config repo located
Doctor DB init SQL located
Java 26 and Postgres images pulled locally
config/discovery/D2/D2R images rebuilt locally
fresh D2R CDS archive trained
D2R with fresh CDS reached health UP
secured Doctor endpoint returned HTTP 200
semantic smoke passed
single runtime screen passed
3-pair V2-C confirmation passed
V2-D attribution passed
```

Fresh D2R CDS archive:

```text
bytes: 126,636,032
sha256: 64A4331695D092148A105ADAA47FEEA0CA46CB0CC561C3289F0413D1A67B6ACC
```

Confirmed result:

```text
comparison: D2 + fresh current-runtime D2 CDS vs D2R + fresh D2R CDS
runtime policy: CDS
launch mode: SPRING_BOOT_FAT_JAR
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
paired wins: 3/3
median PSS delta: -5,156 KB
median Private_Dirty delta: -5,212 KB
median memory.current delta: -6,975,488 bytes
```

V2-D attribution:

```text
anonymous_rw PSS median delta: -3,460 KB
mapped-file PSS median delta: -1,642 KB
NMT metaspace committed median delta: -843 KB
NMT class committed median delta: -262 KB
heap PSS median delta: 0 KB
```

See:

- [V2-K Target Selection](v2-k/v2k-target-selection.md)
- [V2-K Doctor Runtime Inventory](v2-k/v2k-doctor-runtime-inventory.md)
- [V2-K Doctor Runtime Inventory Update](v2-k/v2k-doctor-runtime-inventory-update.md)
- [V2-K Doctor Runtime Unblock Gate](v2-k/v2k-doctor-runtime-unblock-gate.md)
- [V2-K Doctor Runtime Recovery Audit](v2-k/v2k-doctor-runtime-recovery-audit.md)
- [V2-K Doctor CDS Policy](v2-k/v2k-doctor-cds-policy.md)
- [V2-K Doctor D2R CDS Training](v2-k/v2k-doctor-d2r-cds-training.md)
- [V2-K Doctor D2R CDS Training Result](v2-k/v2k-doctor-d2r-cds-training-result.md)
- [V2-K Doctor Image Rebuild Plan](v2-k/v2k-doctor-image-rebuild.md)
- [V2-K Doctor Image Rebuild Result](v2-k/v2k-doctor-image-rebuild-result.md)
- [V2-K Doctor Runtime Materialization Proof](v2-k/v2k-doctor-runtime-materialization-proof.md)
- [V2-K Doctor Runtime Materialization Proof Result](v2-k/v2k-doctor-runtime-materialization-proof-result.md)
- [V2-K Doctor Semantic Smoke](v2-k/v2k-doctor-semantic-smoke.md)
- [V2-K Doctor Semantic Smoke Result](v2-k/v2k-doctor-semantic-smoke-result.md)
- [V2-K Doctor D2 Baseline Smoke](v2-k/v2k-doctor-d2-baseline-smoke.md)
- [V2-K Doctor D2R Candidate Smoke](v2-k/v2k-doctor-d2r-candidate-smoke.md)
- [V2-K Doctor Runtime Screen](v2-k/v2k-doctor-runtime-screen.md)
- [V2-K Doctor Runtime Screen Result](v2-k/v2k-doctor-runtime-screen-result.md)
- [V2-K Doctor Runtime Confirmation](v2-k/v2k-doctor-confirmation.md)
- [V2-K Doctor V2-C Validation](v2-k/v2k-doctor-v2c-validation.md)
- [V2-K Doctor V2-D Attribution](v2-k/v2k-doctor-v2d-attribution.md)
- [V2-K Doctor Final Verdict](v2-k/v2k-doctor-final-verdict.md)
- [V2-K Doctor Final Blocked Root Cause](v2-k/v2k-doctor-final-blocked-root-cause.md)
- [V2-K Closure Outcomes](v2-k/v2k-closure-outcomes.md)
- [V2-K Doctor Runtime Blocked](v2-k/v2k-doctor-runtime-blocked.md)
- [V2-K Doctor Runtime Recovery Result](v2-k/v2k-doctor-runtime-recovery-result.md)

## V2-L Public Visits-Service Runtime Confirmation

V2-L selected Spring PetClinic visits-service as the second public runtime
target. A visits-specific full-P2 artifact was unavailable, so the phase used
the honest comparison:

```text
public visits-service baseline
vs
the same baseline + productized raw LVT/LVTT reducer
```

Artifact and safety result:

```text
dependency JARs processed: 161
static classes scanned: 54,079
classes reduced and byte-audited: 29,701
failed audits: 0
dependency-layer compressed-byte delta: -3,515,600 bytes
BOOT-INF/lib outputs materialized: 161/161
materialization hash mismatches: 0
```

Runtime protocol:

```text
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
embedded HSQLDB standalone profile
```

Confirmed result:

```text
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
paired wins: 3/3
median PSS delta: -2,012 KB
median Private_Dirty delta: -1,680 KB
median memory.current delta: -1,712,128 bytes
```

V2-D attribution:

```text
anonymous_rw PSS median delta: -5,264 KB
NMT metaspace committed median delta: -2,814 KB
NMT total committed median delta: -1,259 KB
heap PSS median delta: +1,988 KB
primary hypothesis: ANONYMOUS_RW_ALLOCATOR_REDUCTION (MEDIUM)
```

The result is not attributed to retained-object or class-count reduction. It is
also not a full-P2, startup, fat-JAR/CDS, all-PetClinic, or universal Spring Boot
claim.

See:

- [V2-L Phase Boundary](v2-l/v2l-phase-open.md)
- [V2-L Visits Runtime Inventory](v2-l/v2l-visits-runtime-inventory.md)
- [V2-L Visits Artifact Build](v2-l/v2l-visits-artifact-build.md)
- [V2-L Visits Artifact Smoke](v2-l/v2l-visits-artifact-smoke.md)
- [V2-L Visits Materialization Proof](v2-l/v2l-visits-materialization-proof.md)
- [V2-L Visits Semantic Smoke](v2-l/v2l-visits-semantic-smoke.md)
- [V2-L Visits Runtime Screen](v2-l/v2l-visits-runtime-screen.md)
- [V2-L Visits Confirmation](v2-l/v2l-visits-confirmation.md)
- [V2-L Visits V2-C Validation](v2-l/v2l-visits-v2c-validation.md)
- [V2-L Visits V2-D Attribution](v2-l/v2l-visits-v2d-attribution.md)
- [V2-L Visits Final Verdict](v2-l/v2l-visits-final-verdict.md)

V2-L's official closure type is `CLOSED_CONFIRMED`; its descriptive phase label
is `CLOSED_CONFIRMED_PUBLIC_SECOND_RUNTIME`.

## V2-M Reducer Recommendation Engine

V2-M converts the V2 artifact/runtime evidence into deterministic product
admission decisions without invoking the reducer.

Maven goal:

```text
jmoa:recommend-reducer
```

Inputs:

```text
normalized reducer-admission-input.json
or canonical reducer/audit/safety/semantic/V2-C/V2-D reports
```

Decisions:

```text
RECOMMEND_CONFIRMED
RECOMMEND_SCREEN_REQUIRED
APPLICATION_LOW_ROI_ARTIFACT_ONLY
APPLICATION_SCREEN_REQUIRED
GENERATED_REPORT_ONLY
GENERATED_MUTATION_BLOCKED
CANDIDATE_FOR_PROTOTYPE
ALLOW_ARTIFACT_ONLY
BLOCK_UNSAFE
BLOCK_SEMANTIC_FAILURE
BLOCK_NO_EVIDENCE
BLOCK_RUNTIME_PROMOTION
DIAGNOSTIC_ONLY
UNKNOWN
```

Historical replay:

```text
V2-I customers raw: RECOMMEND_CONFIRMED / PUBLIC
V2-K Doctor raw: RECOMMEND_CONFIRMED / PRIVATE
V2-L visits raw: RECOMMEND_CONFIRMED / PUBLIC
V2-H hardened ASM: BLOCK_RUNTIME_PROMOTION
V2-J Doctor pre-runtime: ALLOW_ARTIFACT_ONLY
V2-Q visits application confirmation failed: BLOCK_RUNTIME_PROMOTION
V2-R low ROI application: APPLICATION_LOW_ROI_ARTIFACT_ONLY
V2-R plausible application ROI: APPLICATION_SCREEN_REQUIRED
V2-R generated report-only: GENERATED_REPORT_ONLY
V2-R generated prototype candidate: CANDIDATE_FOR_PROTOTYPE
V2-R generated unsafe: GENERATED_MUTATION_BLOCKED
V2-S static large: GENERATED_REPORT_ONLY
V2-S runtime-relevant unsafe: GENERATED_MUTATION_BLOCKED
V2-S bounded safe fixture: CANDIDATE_FOR_PROTOTYPE
passed: 14/14
```

The actual V2-L report bundle was also ingested through analyze mode and
resolved to `RECOMMEND_CONFIRMED`, `PUBLIC`, with an exact protocol match.

See:

- [V2-M Phase Boundary](v2-m/v2m-phase-open.md)
- [V2-M Evidence Inventory Model](v2-m/v2m-evidence-inventory-model.md)
- [V2-M Admission States](v2-m/v2m-admission-states.md)
- [V2-M Recommendation Rules](v2-m/v2m-recommendation-rules.md)
- [V2-M Historical Replay](v2-m/v2m-historical-recommendation-replay.md)
- [V2-M V2-L Recommendation Proof](v2-m/v2m-v2l-recommendation-proof.md)
- [V2-M Closure Report](v2-m/v2m-closure-report.md)

## V2-N Runtime Policy Recommendation Engine

V2-N turns the confirmed runtime protocols into a report-only policy registry
and decision engine. It does not enable the reducer, change deployment, train
CDS, or reuse an archive.

```text
V2-I customers raw no-CDS: RECOMMEND_CONFIRMED_POLICY / PUBLIC
V2-L visits raw no-CDS: RECOMMEND_CONFIRMED_POLICY / PUBLIC
V2-K Doctor D2R raw CDS: RECOMMEND_CONFIRMED_POLICY / PRIVATE
D2R with old D2 CDS: BLOCK_CDS_ARCHIVE_MISMATCH
Doctor no-CDS against CDS baseline: BLOCK_POLICY_MISMATCH
V2-H hardened ASM: BLOCK_RUNTIME_PROMOTION
historical replay: 7/7 passed
```

The canonical V2-K final report bundle was also ingested directly and resolved
to `RECOMMEND_CONFIRMED_POLICY / PRIVATE`. When both a superseded plan and final
result are available, V2-N prefers the final `-result` evidence record.

See:

- [V2-N Phase Boundary](v2-n/v2n-phase-open.md)
- [V2-N Runtime Protocol Registry](v2-n/v2n-runtime-protocol-registry.md)
- [V2-N Policy Rules](v2-n/v2n-runtime-policy-rules.md)
- [V2-N Runtime Preflight](v2-n/v2n-runtime-preflight.md)
- [V2-N Historical Replay](v2-n/v2n-runtime-replay.md)
- [V2-N Canonical Doctor Ingestion Proof](v2-n/v2n-doctor-canonical-ingestion-proof.md)
- [V2-N Claim Boundary](v2-n/v2n-claim-boundary.md)
- [V2-N Closure Report](v2-n/v2n-closure-report.md)

## V2-O Runtime Policy Automation

V2-O makes the V2-N runtime-policy decision actionable without changing its
report-only boundary. It adds SHA-backed preflight, an explicit CDS training
record helper, runtime materialization proof, semantic smoke, V2-C-native
paired capture, and a wrapper that invokes V2-C and V2-D in order.

V2-O does not choose a runtime policy, mutate an artifact, train CDS without an
explicit caller command, or make a runtime performance claim. A ready
preflight, passed smoke, or screen only admits the next evidence gate.

See:

- [V2-O Runtime Automation Guide](v2-o/v2o-runtime-automation-guide.md)
- [V2-O Runtime Preflight](v2-o/v2o-preflight.md)
- [V2-O Materialization Proof Helper](v2-o/v2o-materialization-proof-helper.md)
- [V2-O Confirmation Wrapper](v2-o/v2o-confirmation-wrapper.md)
- [V2-O Final Verdict](v2-o/v2o-final-verdict.md)
- [V2-O Closure Report](v2-o/v2o-closure-report.md)

## V2-Q Generated And Application Metadata Admission

V2-Q extends the raw reducer only to an explicit packaged application-class
directory. Ordinary application classes are eligible for LVT/LVTT-only
reduction; generated, proxy, lambda, Kotlin synthetic, javac synthetic, and
unknown families remain report-only or blocked.

Public visits-service artifact and semantic gates passed: four ordinary classes
were reduced and byte-audited (`480` bytes), two javac-synthetic classes were
preserved, and four endpoint requests completed with zero errors. The first
incremental screen failed, a clean diagnostic rerun passed, and V2-Q therefore
ran a fresh 3-pair confirmation. Confirmation failed with `1/3` paired wins and
median deltas of `+5,732 KB` PSS, `+5,760 KB` Private_Dirty, and `+5,922,816`
bytes `memory.current`.

Closure: `CONFIRMATION_FAILED`. No generated/application runtime-memory claim
exists. Application-class raw reduction remains artifact/semantic-only unless a
future target crosses the ROI threshold or earns its own runtime confirmation.

## V2-R Application / Generated ROI Discovery

V2-R closes the immediate lesson from V2-Q: application/generated mutation
should not continue by rerunning a low-ROI failed candidate. V2-R is report-only
and adds explicit discovery states to the reducer recommendation model:

```text
APPLICATION_LOW_ROI_ARTIFACT_ONLY
APPLICATION_SCREEN_REQUIRED
GENERATED_REPORT_ONLY
GENERATED_MUTATION_BLOCKED
CANDIDATE_FOR_PROTOTYPE
```

V2-R classifies the public visits application reducer result as low ROI
(`480` bytes, four classes, confirmation failed). It also identifies larger
generated-family surfaces such as Spring Data generated helpers as discovery
signals, not mutation admissions, because runtime relevance is not yet proven.

See:

- [V2-R Phase Boundary](v2-r/v2r-phase-open.md)
- [V2-R Application Surface Census](v2-r/v2r-application-surface-census.md)
- [V2-R Runtime Relevance](v2-r/v2r-runtime-relevance.md)
- [V2-R ROI Thresholds](v2-r/v2r-application-roi-thresholds.md)
- [V2-R Candidate Ranking](v2-r/v2r-candidate-ranking.md)
- [V2-R Final Verdict](v2-r/v2r-final-verdict.md)

## V2-S Generated-Family Runtime Relevance

V2-S takes V2-R's discovery signals through a stable family registry and a
diagnostic static-to-runtime reconciliation. The actual PetClinic customers
capture observed runtime-only lambda implementation classes; its Spring Data
surface remained static-only in that capture. Doctor and visits lack the
matched diagnostics needed for a prototype admission. V2-S therefore closes as
infrastructure with no generated-family mutation admitted.

See:

- [V2-S Phase Boundary](v2-s/v2s-phase-open.md)
- [V2-S Runtime Capture](v2-s/v2s-runtime-generated-family-capture.md)
- [V2-S Safety Matrix](v2-s/v2s-generated-family-safety-matrix.md)
- [V2-S ROI Ranking](v2-s/v2s-generated-family-roi-ranking.md)
- [V2-S Final Verdict](v2-s/v2s-final-verdict.md)

## V2-T Generated-Family Matched Evidence

V2-T adds `jmoa:analyze-generated-evidence`, which requires equal static and
capture SHA-256 values before joining a generated-class inventory to diagnostic
lifecycle captures. Recovered customers evidence has a workload capture but no
hashes or startup/warmup captures; Doctor has a static inventory but no matching
lifecycle capture; visits has no matching inventory/capture pair. V2-T closes as
`PARTIAL_INFRASTRUCTURE`, with no family mutation or prototype admission.

See:

- [V2-T Phase Boundary](v2-t/v2t-phase-open.md)
- [V2-T Deduplicated Census](v2-t/v2t-deduplicated-family-census.md)
- [V2-T Reconciliation](v2-t/v2t-static-runtime-reconciliation.md)
- [V2-T Prototype Admission](v2-t/v2t-prototype-admission.md)
- [V2-T Final Verdict](v2-t/v2t-final-verdict.md)

## V2-U Matched Generated-Family Evidence Campaign

V2-U keeps the generated-family track in evidence-campaign mode. It adds
`scripts/capture-generated-lifecycle.ps1` for startup, warmup, and workload
diagnostic captures and extends `jmoa:analyze-generated-evidence` to require a
full identity tuple:

```text
artifactSha256
service
launchMode
runtimePolicy
reducerEngine
familyRegistryVersion
scannerVersion
```

The currently available local evidence still does not satisfy the full matched
bundle contract. Customers has useful historical runtime-only lambda and
histogram signal but no complete fingerprinted startup/warmup/workload bundle.
Visits has reducer/runtime evidence but no matching generated-family inventory
and lifecycle capture. Doctor has static generated-family inventory but no
fresh D2R matched lifecycle bundle. V2-U therefore closes as
`PARTIAL_INFRASTRUCTURE`, with no generated-family prototype admitted.

See:

- [V2-U Phase Boundary](v2-u/v2u-phase-open.md)
- [V2-U Capture Harness](v2-u/v2u-generated-capture-harness.md)
- [V2-U Customers Evidence](v2-u/v2u-customers-matched-evidence.md)
- [V2-U Visits Evidence](v2-u/v2u-visits-matched-evidence.md)
- [V2-U Doctor D2R Evidence](v2-u/v2u-doctor-d2r-matched-evidence.md)
- [V2-U Lifecycle Reconciliation](v2-u/v2u-lifecycle-reconciliation.md)
- [V2-U Final Verdict](v2-u/v2u-final-verdict.md)

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

V2-K adds a private Doctor runtime confirmation for corrected D2 vs raw-reduced
D2R under a Spring Boot fat-JAR/CDS protocol with variant-specific CDS archives.
That claim is not public-reproducible and is not transferred to all Doctor
deployments, all fat-JAR services, all CDS/AppCDS modes, startup performance, or
generated-class mutation.

V2-L adds a second public runtime confirmation for visits-service baseline vs
the same baseline plus the raw reducer. It is intentionally separate from the
customers-service full-P2 comparisons and does not establish a full-P2,
fat-JAR/CDS, startup, all-PetClinic, or universal Spring Boot claim.

V2-M adds no runtime result and changes no reducer behavior. A confirmed
recommendation is valid only for an exact evidence-backed service, launch mode,
and runtime policy. Any different protocol returns to screen/confirmation gates.

## V2-W Matched Capture Execution

V2-W executed the frozen generated-family campaign against exact Customers,
Visits, and Doctor D2R artifacts. All three startup/warmup/workload bundles
reached `MATCHED_DIAGNOSTIC_EVIDENCE`, spanning exploded Boot/no-CDS and
fat-JAR/CDS. No generated-family prototype was admitted: lambda is the existing
v1 domain, Spring Data and compiler-generated surfaces lack a bounded safe
transform, and AOT/proxy families remain safety blocked. The generated mutation
track is `CLOSED_DISCOVERY_ONLY_FOR_V2`.

See [V2-W Final Verdict](v2-w/v2w-final-verdict.md).
