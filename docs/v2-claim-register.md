# V2 Claim Register

This is the source of truth after final performance reconciliation.

Closure terms follow:

- [V2 Phase Closure Taxonomy](v2-phase-closure-taxonomy.md)

## Final Product Gate

The reproducible final public customers-service release gate is the incremental
V1-to-V2 comparison. The direct B0-to-V2 historical result is preserved but is
not an RC2 claim because its raw capture is unavailable and a fresh five-pair
exact-image replication was mixed.

```text
comparison: finalized V1 vs final V2 product artifact
valid runs: 6/6
paired wins: 2/3
median PSS delta: -6,012 KB
median Private_Dirty delta: -5,708 KB
median memory.current delta: -8,081,408 bytes
decision: CONFIRMED_WIN

comparison: B0 baseline vs final V2 product artifact (fresh RC2 replication)
valid runs: 10/10
paired wins: 2/5
median PSS delta: +585 KB
median Private_Dirty delta: +844 KB
median memory.current delta: -1,363,968 bytes
decision: MIXED_METRICS_NEEDS_RERUN; not claimable
```

The claim is limited to public customers-service under `EXPLODED_BOOT_APP`,
`NO_CDS_LOW_DIRTY`, `MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden/javaagent, and
the balanced cold-page-cache protocol. It is not a universal or startup claim.

## Three-Service Acceptance

The frozen final V1-to-V2 launch gate now passes under service-specific
confirmed runtime policies:

```text
status: READY_FOR_V2_FINAL
PetClinic: PASS (NO_CDS_LOW_DIRTY; median PSS -6,012 KB)
Doctor: PASS (CDS; median PSS -5,156 KB)
Patient: PASS (JDK_BASE_CDS_LOW_DIRTY; 6/6 valid runs, 3/3 paired wins, median PSS -8,279 KB)
Patient secondary policy: PASS (NO_CDS_LOW_DIRTY; median PSS -8,903 KB)
Patient application CDS: BLOCK_RUNTIME_PROMOTION
```

The Patient stock-base-CDS and no-CDS evidence sets are independently valid,
V2-C-confirmed, and V2-D-attributed. The failed application-archive studies
remain policy-specific failures. The aggregate claim is service-policy scoped:
it does not transfer between base CDS, application CDS, and no-CDS.

See [the final three-service matrix](v2-final/v2-three-service-memory-matrix.md)
and [the Patient policy verdict](v2-final/patient-final-policy-verdict.md).
The preserved CDS failure is recorded in
[patient-cds-final-verdict.md](v2-final/patient-cds-final-verdict.md).

## Confirmed Runtime Claims

### 1. PetClinic Full P2 No-CDS Win

JMOA full P2 confirmed a public no-CDS PetClinic memory win under:

```text
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
```

Claim summary:

```text
median PSS reduction: about 4.6 MB
source: Phase 33M public case study
```

### 2. V2-E Incremental PetClinic Reducer Win

V2-E confirmed an incremental runtime win over full P2:

```text
comparison: full P2 vs full P2 + V2-E LVT/LVTT reducer
paired wins: 2/3
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
```

Scope:

```text
Spring PetClinic customers-service
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
earlier V2-E reducer policy used for v0.7.0
```

This claim is not transferred to the later V2-F-hardened/productized reducer.

### 3. V2-I Raw Reducer PetClinic Win

V2-I confirmed an incremental runtime win over full P2 using the explicit raw
reducer engine:

```text
comparison: full P2 vs full P2 + V2-I raw LVT/LVTT reducer
paired wins: 2/3
median PSS delta: -4,467 KB
median Private_Dirty delta: -4,208 KB
median memory.current delta: -4,493,312 bytes
```

Scope:

```text
Spring PetClinic customers-service
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
jmoa.reducer.engine=raw
```

This claim is separate from the older V2-E claim and from the V2-F/V2-H
hardened `asm` reducer result.

### 4. V2-K Doctor Raw Reducer Runtime Win

V2-K confirmed an incremental runtime win for the private Doctor corrected D2
runtime:

```text
comparison: D2 + fresh current-runtime D2 CDS vs D2R + fresh D2R CDS
launch mode: SPRING_BOOT_FAT_JAR
runtime policy: CDS
paired wins: 3/3
median PSS delta: -5,156 KB
median Private_Dirty delta: -5,212 KB
median memory.current delta: -6,975,488 bytes
```

Scope:

```text
private Doctor corrected D2 runtime stack
raw-reduced D2R artifact
variant-specific CDS archives
Java 26 container runtime
class-load logging disabled during memory pairs
JFR disabled
```

This claim is not public-reproducible and is not transferred to all Doctor
deployments, all fat-JAR services, all CDS/AppCDS modes, or startup performance.

### 5. Final Patient Runtime Wins

The primary final Patient matrix comparison passes with the identical stock JDK
base archive mapped in both arms:

```text
comparison: accepted Patient V1 vs corrected Patient V2
launch mode: SPRING_BOOT_FAT_JAR
runtime policy: JDK_BASE_CDS_LOW_DIRTY
paired wins: 3/3
median PSS delta: -8,279 KB
median Private_Dirty delta: -8,444 KB
median memory.current delta: -8,523,776 bytes
```

Both arms used the same stock `classes_coh.jsa` bytes, no Patient application
archive, `MALLOC_ARENA_MAX=1`, and no runtime javaagent. V2-C returned
`CONFIRMED_WIN`; V2-D identified `HEAP_PAGE_TOUCH_REDUCTION` as the primary
attribution.

The independent no-CDS comparison also passes:

```text
comparison: corrected final V1 vs corrected final V2
launch mode: SPRING_BOOT_FAT_JAR
runtime policy: NO_CDS_LOW_DIRTY
paired wins: 2/3
median PSS delta: -8,903 KB
median Private_Dirty delta: -8,636 KB
median memory.current delta: -9,707,520 bytes
```

Scope:

```text
private Patient service
Java 26, Serial GC, MALLOC_ARENA_MAX=1
CDS/AppCDS/Leyden disabled
no runtime javaagent
600-request workload, T+20 capture, balanced cache-reset pairs
```

These claims are policy-specific. Dynamic Patient application CDS remains
blocked. Neither result transfers to an application archive or another Patient
deployment.

### 6. V2-L Visits Raw Reducer Runtime Win

V2-L confirmed the productized raw reducer on a second public runtime target:

```text
comparison: public visits baseline vs the same baseline + raw LVT/LVTT reducer
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
paired wins: 3/3
median PSS delta: -2,012 KB
median Private_Dirty delta: -1,680 KB
median memory.current delta: -1,712,128 bytes
```

Scope:

```text
Spring PetClinic visits-service
public source revision 305a1f13e4f961001d4e6cb50a9db51dc3fc5967
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
embedded HSQLDB standalone protocol
jmoa.reducer.engine=raw
```

No visits-specific full-P2 artifact was available. This claim is baseline vs
baseline plus reducer, not full P2 vs full P2 plus reducer. It is not transferred
to all PetClinic/Spring Boot services, fat-JAR/CDS modes, or startup performance.

V2-L's official closure type is `CLOSED_CONFIRMED`. Its descriptive phase label
is `CLOSED_CONFIRMED_PUBLIC_SECOND_RUNTIME`.

## Product Capability Claims

### V2-M Reducer Recommendation Engine

V2-M adds a report-only `jmoa:recommend-reducer` Maven goal that:

```text
normalizes reducer, audit, safety, semantic, V2-C, and V2-D evidence
applies safety-first admission rules
distinguishes public/private/internal confirmation scope
requires an exact service, launch-mode, and runtime-policy match
emits JSON and Markdown recommendations
replays historical decisions with mismatch failure
```

Historical replay passed 14/14 cases, including the V2-Q failed
application-class confirmation and V2-R application/generated discovery
classifications plus V2-S generated-family safety/relevance states. This is a tooling capability claim, not a new runtime
performance claim. The goal does not enable or invoke mutation.

### V2-R Application / Generated ROI Discovery

V2-R adds report-only application/generated ROI classifications to
`jmoa:recommend-reducer`:

```text
APPLICATION_LOW_ROI_ARTIFACT_ONLY
APPLICATION_SCREEN_REQUIRED
GENERATED_REPORT_ONLY
GENERATED_MUTATION_BLOCKED
CANDIDATE_FOR_PROTOTYPE
```

It classifies the V2-Q visits application result as low ROI and
confirmation-failed, and it treats larger generated-family surfaces as discovery
signals until runtime relevance and family-specific safety are proven. V2-R
does not enable generated/application mutation and creates no runtime
performance claim.

### V2-S Generated-Family Runtime Relevance

V2-S adds a report-only `jmoa:analyze-generated-relevance` goal and a stable
family registry. It distinguishes static packaged classes, diagnostic
class-load events, and histogram objects, and keeps CGLIB, JDK proxy,
ByteBuddy, Hibernate proxy, and Spring AOT families mutation-blocked by
default. The V2-S analysis did not admit a family prototype and adds no
generated-class runtime claim.

### V2-T Generated-Family Matched Evidence

V2-T adds a report-only `jmoa:analyze-generated-evidence` goal. It produces an
exclusive primary-family census and only reconciles static inventory with
diagnostic startup/warmup/workload captures when non-empty artifact SHA-256
values match. Current recovered customers and Doctor reports are deliberately
classified as incomplete because that exact evidence contract is not satisfied.
V2-T admits no family prototype, changes no mutation behavior, and adds no
runtime performance claim. The V2-S bounded-safe candidate remains a test
fixture, not a real family admission.

### V2-U Matched Generated-Family Evidence Campaign

V2-U keeps `jmoa:analyze-generated-evidence` report-only and adds a reusable
startup/warmup/workload diagnostic capture harness:

```text
scripts/capture-generated-lifecycle.ps1
```

The analyzer now requires the full static/capture identity tuple before any
matched generated-family lifecycle result can be treated as evidence:

```text
artifactSha256
service
launchMode
runtimePolicy
reducerEngine
familyRegistryVersion
scannerVersion
```

It emits explicit non-admission statuses for missing or mismatched identity,
including `ARTIFACT_FINGERPRINT_MISSING`, `ARTIFACT_FINGERPRINT_MISMATCH`,
`SERVICE_MISMATCH`, `REGISTRY_VERSION_MISMATCH`, and
`LIFECYCLE_CAPTURE_INCOMPLETE`. Current customers, visits, and Doctor D2R
evidence remains incomplete under this stricter contract, so V2-U admits no
generated-family prototype, changes no mutation behavior, and adds no runtime
performance claim.

### V2-V Fresh Matched Generated-Family Capture Campaign

V2-V adds the executable campaign layer around V2-U:

```text
preflight
raw startup/warmup/workload attribution
strict bundle validation
lifecycle matrix
cross-service family matrix
matched-only ROI ranking
one-candidate admission gate
```

The campaign remains `PARTIAL_INFRASTRUCTURE` until fresh customers, visits,
and Doctor D2R bundles each return `MATCHED_DIAGNOSTIC_EVIDENCE`. V2-V admits
no prototype, changes no mutation behavior, and adds no runtime performance
claim.

### V2-N Runtime Policy Recommendation Engine

V2-N adds a report-only `jmoa:recommend-runtime` Maven goal that:

```text
matches exact service, launch mode, runtime policy, and reducer-engine scope
registers public and private confirmed runtime protocols
requires fresh artifact/archive pairing and mapped-archive proof for CDS
blocks CDS/no-CDS mixed comparisons
reports preflight gaps without changing deployment
replays historical policy decisions with mismatch failure
```

Historical replay passed 7/7 cases, including the V2-I and V2-L public no-CDS
policies, the V2-K private D2R CDS policy, stale-archive rejection, policy
mismatch rejection, and the V2-H failed-screen block. This is a tooling
capability claim, not a new runtime performance claim. The goal does not train
or reuse CDS archives and does not alter runtime configuration.

### V2-O Runtime Policy Automation

V2-O adds a report-only runtime workflow around the existing policy and evidence
engines:

```text
SHA-backed preflight
explicit CDS training records
materialization proof
semantic smoke
V2-C-native pair capture
V2-C/V2-D confirmation wrapper
```

It standardizes inputs and output folders but does not enable mutation, select
a runtime policy, start a private stack implicitly, or produce a runtime claim.
V2-C remains the verdict gate and V2-D remains the attribution gate.

### V2-P Runtime Workflow Productization

V2-P adds a script-first coordinator and claim-register consistency guard for
the established V2-M, V2-N, V2-O, V2-C, and V2-D gates. It may report
`CLAIM_ALLOWED` only as a human-review eligibility state after existing V2-C
and V2-D evidence is supplied. It never updates this register automatically
and creates no runtime performance claim.

## Artifact-Only Claims

```text
V2-F PetClinic hardened artifact smoke:
  removed bytes: 3,870,720
  runtime claim: false

V2-F Doctor corrected D2 artifact smoke:
  removed bytes: 4,156,014
  runtime claim: false

V2-G Doctor corrected D2 artifact generalization:
  removed dependency-jar bytes: 4,156,014
  BOOT-INF/lib entries replaced in materialized fat JAR: 184
  runtime claim: false

V2-H PetClinic hardened/productized reducer screen:
  materialized dependency jar byte delta: -3,855,370
  BOOT-INF/lib entries replaced: 162
  screen PSS delta: +7,804 KB
  screen Private_Dirty delta: +7,824 KB
  runtime claim: false

V2-I PetClinic raw reducer materialization:
  materialized dependency jar byte delta: -3,668,109
  BOOT-INF/lib entries replaced: 162
  runtime claim: true under the V2-I PetClinic scope above

V2-J Doctor raw artifact smoke:
  removed dependency-jar bytes: 3,926,870
  classes reduced and audited: 31,942
  failed raw byte-preservation audits: 0
  runtime claim: false

V2-K Doctor raw materialization:
  BOOT-INF/lib entries replaced: 184/184
  materialized raw D2R fat-JAR SHA-256 recorded
  runtime claim: true under the V2-K Doctor scope above

V2-L visits raw materialization:
  materialized dependency jar byte delta: -3,515,600
  BOOT-INF/lib entries replaced: 161/161
  classes reduced and byte-audited: 29,701
  failed byte-preservation audits: 0
  runtime claim: true under the V2-L visits scope above
```

## Not Claimed

```text
V2-F-hardened/productized asm reducer runtime win
startup win
public Doctor reproducibility
all Doctor deployments
all fat-JAR services
all CDS/AppCDS runtime modes
universal public cross-service generalization
all Spring PetClinic or Spring Boot services
visits-service fat-JAR or CDS/AppCDS improvement
all debug metadata stripping safety
LineNumberTable stripping
StackMapTable stripping
annotation stripping
Signature stripping
BootstrapMethods rewriting or stripping
CGLIB/JDK proxy rewriting
Spring AOT generated-class mutation
automatic reducer mutation from a V2-M recommendation
transfer of a recommendation to a different service, launch mode, or runtime policy
automatic runtime policy application from a V2-N recommendation
CDS archive reuse across artifact variants
transfer of CDS evidence to no-CDS or no-CDS evidence to CDS
automatic runtime performance claim from V2-O preflight, training, smoke, or screen output
V2-R generated/application runtime improvement
V2-R generated/application mutation
V2-S generated-family runtime improvement
V2-S generated-family mutation
V2-T generated-family runtime improvement
V2-T generated-family mutation
V2-T generated-family prototype admission
V2-U generated-family runtime improvement
V2-U generated-family mutation
V2-U generated-family prototype admission
V2-U cross-service generated-family runtime generalization
V2-W generated-family runtime improvement
V2-W generated-family mutation
V2-W generated-family prototype admission
startup improvement from the final V2 customers acceptance
transfer of final customers acceptance to other services or runtime policies
```

Any new runtime performance claim must pass V2-C validation and V2-D attribution.

## V2-Q Application Metadata Admission

V2-Q admits only ordinary packaged application classes to opt-in raw LVT/LVTT
reduction. On public visits-service, four ordinary classes were reduced by
`480` bytes with four successful raw preservation audits; two
`JAVAC_SYNTHETIC` classes remained report-only. Materialization and semantic
smoke passed. The first screen failed, one diagnostic rerun passed, and the
fresh 3-pair confirmation failed with `1/3` paired wins and median deltas of
`+5,732 KB` PSS, `+5,760 KB` Private_Dirty, and `+5,922,816` bytes
`memory.current`. V2-Q makes no runtime claim.
