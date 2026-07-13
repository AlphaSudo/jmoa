# JMOA

Build-time JVM/Spring Boot memory optimization toolkit.

JMOA rewrites selected Java lambda and adapter call sites at build time,
materializes the optimized output into the runtime deployment shape, verifies
that optimized classes are actually loaded, and measures JVM/container memory
with PSS-oriented evidence.

The case-study portfolio is published separately:

- Portfolio: https://github.com/AlphaSudo/jmoa-jvm-optimization-portfolio
- V2 claim register: [docs/v2-claim-register.md](docs/v2-claim-register.md)
- V2 foundation closure: [docs/v2-foundation-closure.md](docs/v2-foundation-closure.md)
- V2 final audit: [docs/v2-final](docs/v2-final/README.md)
- V2 final performance gate: [docs/v2-final/v2-final-performance-acceptance.md](docs/v2-final/v2-final-performance-acceptance.md)

## What This Is

JMOA is a source release for the tooling behind the published portfolio:

- `jmoa-maven-plugin`: build-time scanner, admission, rewrite, packaging, and
  measurement support
- `jmoa-runtime-lib`: runtime adapter support used by rewritten artifacts
- `tools/mode-c-launcher`: deterministic classpath launch helper, kept outside
  the default CI reactor until its process-exit integration tests are split out
- `examples/spring-petclinic-customers-nocds`: public PetClinic reproduction
  workflow scaffold
- `docs`: architecture, runtime-origin verification, materialization,
  generated-class inventory, bytecode-size profiling, and measurement
  methodology

## What This Is Not

- Not a runtime javaagent for the final optimized service
- Not a universal memory-win guarantee
- Not a replacement for measuring PSS, Private_Dirty, and cgroup memory
- Not tied to one Spring Boot deployment shape
- Not a publication of private HMS, patient-service, or doctor-service source

## Confirmed Case Studies

The source here supports the evidence portfolio, which currently summarizes:

| Case | Runtime shape | CDS mode | Confirmed result |
| --- | --- | --- | --- |
| Patient-service | expanded classpath | CDS/AppCDS-style | ~4.2-4.4 MB median memory reduction |
| Doctor-service | corrected Spring Boot fat JAR | CDS | ~2.7 MB median PSS reduction |
| Spring PetClinic customers-service | exploded Boot / JarLauncher | no CDS | ~4.6 MB median PSS reduction |

The PetClinic result is the public reproducibility bridge. It is scoped to the
project's exploded Boot launch shape and the `NO_CDS_LOW_DIRTY` runtime policy.

## V2 Final Release Gate

V2-A through V2-W are closed within their declared boundaries, but the final V2
publication gate is not green yet. A fresh direct public `B0 -> V2` PetClinic
customers-service run completed with valid evidence and failed the release
performance threshold:

```text
comparison: B0 baseline vs final V2 product artifact
valid runs: 6/6
paired wins: 1/3
median PSS delta: +274 KB
median Private_Dirty delta: +464 KB
median memory.current delta: +225,280 bytes
release decision: RELEASE_PERFORMANCE_GATE_FAILED
```

The confirmed incremental results remain valid within their measured scopes,
but they must not be added together or reused as a final V2-over-baseline
headline. See the final audit docs for the release blockers and allowed claim
boundary:

- [V2 final performance acceptance](docs/v2-final/v2-final-performance-acceptance.md)
- [V2 release blockers](docs/v2-final/v2-release-blockers.md)
- [V2 claim matrix](docs/v2-final/v2-claim-matrix.md)

## Build

Prerequisites:

- JDK 22 for the Maven plugin module
- JDK 17+ for the runtime library
- Maven 3.9+

From the repository root:

```powershell
./scripts/build-all.ps1
```

or:

```bash
./scripts/build-all.sh
```

The default Maven reactor builds the plugin and runtime library. It does not run
Podman, full PetClinic integration measurements, or the launcher process-exit
integration tests by default.

## PetClinic Reproduction

See [examples/spring-petclinic-customers-nocds](examples/spring-petclinic-customers-nocds/README.md).

The example is intentionally explicit about the claim boundary:

- no CDS
- no AppCDS
- no Leyden
- no runtime javaagent
- `MALLOC_ARENA_MAX=1`
- exploded Boot / `JarLauncher`
- dynamic runtime-origin verification

## V2-A Generated Class Optimizer

V2-A starts the generated/synthetic/proxy/AOT class expansion behind an explicit
feature flag. The current implementation inventories generated class shapes,
can attribute runtime generated-class cost from class-load logs and class
histograms, writes a safety taxonomy, selects the first prototype family, and
feeds generated-class features into a V2 ROI report.

Generated-class bytecode mutation remains disabled.

```powershell
mvn process-classes `
  -Djmoa.synthetic.enabled=true `
  -Djmoa.synthetic.inventoryOnly=true `
  -Djmoa.synthetic.optimizeFamily=none
```

See:

- [Generated Class Optimizer](docs/generated-class-optimizer.md)
- [Synthetic Class Safety Model](docs/synthetic-class-safety-model.md)
- [Spring AOT Generated-Class Optimization](docs/spring-aot-generated-class-optimization.md)
- [Proxy Optimization Non-Goals](docs/proxy-optimization-non-goals.md)

## V2-S Generated-Family Runtime Relevance

V2-S makes the V2-A generated-family inventory actionable without allowing
generated-class mutation. `jmoa:analyze-generated-relevance` reconciles a
static V2-A inventory with a separate diagnostic class-load/histogram capture,
publishes family safety and ROI components, and feeds the result into the
report-only recommendation engine.

```powershell
mvn -N jmoa:analyze-generated-relevance `
  '-Djmoa.generatedRelevance.enabled=true' `
  '-Djmoa.generatedRelevance.inputDir=<v2-a-report-dir>' `
  '-Djmoa.generatedRelevance.outputDir=<output-dir>' `
  '-Djmoa.generatedRelevance.service=<service>'
```

Class-load logging and histograms are diagnostic-only inputs; they are never
mixed into a claimable V2-C memory pair. V2-S closed without admitting a
generated-family mutation prototype. See [the final verdict](docs/v2-s/v2s-final-verdict.md).

## V2-T Generated-Family Matched Evidence

V2-T tightens the diagnostic contract before any future generated-family
prototype can be considered. `jmoa:analyze-generated-evidence` builds an
exclusive primary-family census and joins a static V2-A inventory to lifecycle
captures only when their artifact SHA-256 values match. A missing fingerprint,
missing startup/warmup/workload capture, or mismatched artifact remains a
report-only non-admission result.

```powershell
mvn -N jmoa:analyze-generated-evidence `
  '-Djmoa.generatedEvidence.enabled=true' `
  '-Djmoa.generatedEvidence.inventory=<generated-class-inventory.json>' `
  '-Djmoa.generatedEvidence.startupCapture=<startup-runtime-attribution.json>' `
  '-Djmoa.generatedEvidence.warmupCapture=<warmup-runtime-attribution.json>' `
  '-Djmoa.generatedEvidence.workloadCapture=<workload-runtime-attribution.json>' `
  '-Djmoa.generatedEvidence.staticArtifactSha256=<sha256>' `
  '-Djmoa.generatedEvidence.captureArtifactSha256=<sha256>' `
  '-Djmoa.generatedEvidence.service=<service>' `
  '-Djmoa.generatedEvidence.launchMode=<launch-mode>' `
  '-Djmoa.generatedEvidence.runtimePolicy=<runtime-policy>' `
  '-Djmoa.generatedEvidence.reducerEngine=<engine>' `
  '-Djmoa.generatedEvidence.familyRegistryVersion=<family-registry-version>' `
  '-Djmoa.generatedEvidence.scannerVersion=<scanner-version>' `
  '-Djmoa.generatedEvidence.outputDir=<output-dir>'
```

V2-T is diagnostic-only and does not rewrite generated classes, create a
runtime claim, or treat the V2-S bounded-safe fixture as an empirical family
admission. See [the V2-T verdict](docs/v2-t/v2t-final-verdict.md).

## V2-U Matched Generated-Family Evidence Campaign

V2-U turns the V2-T contract into a reusable capture campaign. It adds a
diagnostic lifecycle capture helper and extends `jmoa:analyze-generated-evidence`
so static inventory and runtime captures must share the same identity tuple:

```text
artifactSha256
service
launchMode
runtimePolicy
reducerEngine
familyRegistryVersion
scannerVersion
```

The capture helper records startup, warmup, and workload diagnostics without
mixing them into claimable V2-C memory pairs:

```powershell
./scripts/capture-generated-lifecycle.ps1 `
  -Service '<service>' `
  -ArtifactPath '<artifact-or-fat-jar>' `
  -OutputDir '<diagnostic-output-dir>' `
  -LaunchMode '<launch-mode>' `
  -RuntimePolicy '<runtime-policy>' `
  -ReducerEngine '<engine>' `
  -FamilyRegistryVersion '<family-registry-version>' `
  -ScannerVersion '<scanner-version>' `
  -PidFile '<pid-file>' `
  -WarmupCommand '<warmup-command>' `
  -WorkloadCommand '<workload-command>'
```

The analyzer can consume that manifest directly:

```powershell
mvn -N jmoa:analyze-generated-evidence `
  '-Djmoa.generatedEvidence.enabled=true' `
  '-Djmoa.generatedEvidence.inventory=<generated-class-inventory.json>' `
  '-Djmoa.generatedEvidence.lifecycleManifest=<generated-lifecycle-manifest.json>' `
  '-Djmoa.generatedEvidence.staticArtifactSha256=<sha256>' `
  '-Djmoa.generatedEvidence.service=<service>' `
  '-Djmoa.generatedEvidence.launchMode=<launch-mode>' `
  '-Djmoa.generatedEvidence.runtimePolicy=<runtime-policy>' `
  '-Djmoa.generatedEvidence.reducerEngine=<engine>' `
  '-Djmoa.generatedEvidence.familyRegistryVersion=<family-registry-version>' `
  '-Djmoa.generatedEvidence.scannerVersion=<scanner-version>' `
  '-Djmoa.generatedEvidence.outputDir=<output-dir>'
```

V2-U closes as partial infrastructure for the currently available local
evidence: customers, visits, and Doctor still lack complete fresh matched
lifecycle bundles. The phase admits no generated-family prototype, enables no
generated-class mutation, and adds no runtime performance claim. See
[the V2-U verdict](docs/v2-u/v2u-final-verdict.md).

## V2-B Bytecode Size Profiler

V2-B adds report-only classfile and method-size profiling. It reports large
classes, large methods, near-64KB method risk, constant-pool footprint,
attribute footprint, and bytecode-size ROI features.

Generated-class labels from V2-A are included where available. Bytecode-size
mutation and debug stripping remain disabled.

```powershell
mvn process-classes `
  -Djmoa.size.enabled=true `
  -Djmoa.size.reportOnly=true
```

See:

- [Bytecode Size Profiler](docs/bytecode-size-profiler.md)
- [Method 64KB Risk](docs/method-64kb-risk.md)
- [Classfile Footprint ROI](docs/classfile-footprint-roi.md)
- [Debug Attribute Stripping](docs/debug-attribute-stripping.md)
- [Large Generated Methods](docs/large-generated-methods.md)

## V2-C Evidence Engine

V2-C adds a report-only measurement stability engine. It parses already-captured
run folders, validates evidence, detects perturbing diagnostics, analyzes
baseline/candidate pairs, classifies variance patterns, and writes confirmation
reports. It does not run containers and does not change optimizer behavior.

```powershell
mvn jmoa:evidence `
  -Djmoa.evidence.enabled=true `
  -Djmoa.evidence.inputDir=<evidence-dir> `
  -Djmoa.evidence.expectedPolicy=NO_CDS_LOW_DIRTY
```

Historical replay mode can enforce audited outcomes when local archived evidence
folders are available:

```powershell
mvn jmoa:evidence `
  -Djmoa.evidence.enabled=true `
  -Djmoa.evidence.mode=replay `
  -Djmoa.evidence.inputDir=<historical-evidence-root> `
  -Djmoa.evidence.replaySuite=docs/v2-c/historical-replay-suite.example.json
```

See:

- [Evidence Engine](docs/evidence-engine.md)
- [V2-C Evidence Schema](docs/v2-c/jmoa-evidence-schema.md)

## V2-D Memory Attribution Engine

V2-D adds a report-only memory attribution layer. It consumes V2-C-valid
evidence and explains why memory moved: heap page touch, retained objects,
anonymous mappings, class/metaspace movement, generated-family context, and
bytecode/runtime correlation. It does not mutate bytecode and does not claim a
new optimizer win by itself.

```powershell
mvn jmoa:attribution `
  -Djmoa.attribution.enabled=true `
  -Djmoa.attribution.inputDir=<v2-c-evidence-dir> `
  -Djmoa.evidence.expectedPolicy=NO_CDS_LOW_DIRTY
```

Optional V2-A and V2-B reports can be supplied to enrich generated-family and
bytecode/runtime attribution:

```powershell
mvn jmoa:attribution `
  -Djmoa.attribution.enabled=true `
  -Djmoa.attribution.inputDir=<v2-c-evidence-dir> `
  -Djmoa.attribution.generatedClassReport=<generated-class-inventory.json> `
  -Djmoa.attribution.bytecodeRuntimeCorrelationReport=<bytecode-runtime-correlation.json>
```

See:

- [V2-D Scope](docs/v2-d/phase-v2d0-scope.md)
- [Memory Category Model](docs/v2-d/memory-category-model.md)
- [Historical Attribution Replay](docs/v2-d/v2d-historical-attribution-replay.md)
- [V2-D Closure Report](docs/v2-d/v2d-closure-report.md)

## V2-E Debug Metadata Reducer

V2-E adds the first controlled post-v1 reducer prototype: an opt-in
release-low-footprint reducer for `LocalVariableTable` and
`LocalVariableTypeTable` in dependency jars. It is disabled by default and
report-only by default.

It does not strip line numbers, source files, stack-map frames, annotations,
signatures, BootstrapMethods, or framework-sensitive metadata. In mutation mode,
the default `asm` engine skips classes that carry `BootstrapMethods` rather than
rewriting them.

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

Mutation requires the explicit release-low-footprint gate:

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.reportOnly=false `
  -Djmoa.reducer.optimize=true `
  -Djmoa.reducer.profile=release-low-footprint `
  -Djmoa.reducer.engine=asm `
  -Djmoa.reducer.stripLocalVariableTable=true `
  -Djmoa.reducer.stripLocalVariableTypeTable=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

V2-I adds an explicit raw engine:

```powershell
mvn jmoa:reduce-bytecode `
  -Djmoa.reducer.enabled=true `
  -Djmoa.reducer.reportOnly=false `
  -Djmoa.reducer.optimize=true `
  -Djmoa.reducer.profile=release-low-footprint `
  -Djmoa.reducer.engine=raw `
  -Djmoa.reducer.stripLocalVariableTable=true `
  -Djmoa.reducer.stripLocalVariableTypeTable=true `
  -Djmoa.reducer.inputDir=<optimized-lib-dir>
```

The raw engine preserves BootstrapMethods and removes only nested local-variable
debug tables from method `Code` attributes. It keeps the V2-F signed,
multi-release, and sealed JAR skip policy.

V2-J productizes the raw engine with per-class byte-preservation auditing. Raw
reducer reports now include:

```text
raw-reducer-byte-preservation-report.json
raw-reducer-byte-preservation-report.md
jmoa-reducer-manifest-v2.json
```

The auditor normalizes original and reduced class bytes by removing only
`LocalVariableTable` and `LocalVariableTypeTable`; all other bytes must match.

See:

- [V2-E Debug Metadata Reducer](docs/v2-e/v2e-debug-metadata-reducer.md)
- [V2-E Reducer Safety Taxonomy](docs/v2-e/v2e-reducer-safety-taxonomy.md)
- [V2-E Failure Handling](docs/v2-e/v2e-failure-handling.md)
- [V2-E Implementation Status](docs/v2-e/v2e-implementation-status.md)
- [V2-E PetClinic Artifact Smoke](docs/v2-e/v2e-petclinic-artifact-smoke.md)
- [V2-E PetClinic Service Smoke](docs/v2-e/v2e-petclinic-service-smoke.md)
- [V2-E Claim Boundary](docs/v2-e/v2e-claim-boundary.md)
- [V2-I Policy Diff](docs/v2-i/v2i-policy-diff.md)
- [V2-I Raw Reducer Final Verdict](docs/v2-i/v2i-final-verdict.md)
- [V2-J Raw Byte Preservation Report](docs/v2-j/v2j-raw-byte-preservation-report.md)
- [V2-J Final Verdict](docs/v2-j/v2j-final-verdict.md)

## V2-F Reducer Productization

V2-F closes product hardening for the V2-E reducer. It does not add a new
runtime claim; it makes the existing reducer safer to run on real dependency
surfaces.

V2-F adds:

- signed JAR skip policy
- multi-release JAR skip policy
- sealed JAR skip policy
- reducer manifest with input/output hashes
- PetClinic hardened artifact smoke
- Doctor artifact-level smoke
- reducer admission policy

See:

- [V2-F Jar Safety Report](docs/v2-f/v2f-jar-safety-report.md)
- [V2-F Reducer Admission Policy](docs/v2-f/v2f-reducer-admission-policy.md)
- [V2-F Productization Status](docs/v2-f/v2f-reducer-productization-status.md)
- [V2 Foundation Closure](docs/v2-foundation-closure.md)
- [V2 Claim Register](docs/v2-claim-register.md)

## V2-G Reducer Generalization

V2-G starts reducer generalization using the existing V2-E LVT/LVTT reducer. It
does not add a new reducer type or broaden metadata stripping.

The first V2-G target is Doctor corrected D2. V2-G currently closes as
artifact-level generalization only: the reducer removed 4,156,014 dependency-jar
bytes and a reduced fat JAR was materialized with all 184 `BOOT-INF/lib` entries
replaced.

V2-K later recovered the local Doctor runtime stack: support images were rebuilt,
a fresh D2R CDS archive was trained for the raw-reduced artifact, and Doctor D2R
passed health plus a secured endpoint smoke. V2-K then completed three valid
pairs and confirmed D2R over D2 at `-5,156 KB` median PSS under the documented
private fat-JAR/CDS protocol.

See:

- [V2-G Target Selection](docs/v2-g/v2g-target-selection.md)
- [V2-G Doctor Artifact Smoke](docs/v2-g/v2g-doctor-artifact-smoke.md)
- [V2-G Doctor Semantic Smoke Blocked](docs/v2-g/v2g-doctor-semantic-smoke-blocked.md)
- [V2-G Final Verdict](docs/v2-g/v2g-final-verdict.md)
- [V2-K Doctor Runtime Recovery Result](docs/v2-k/v2k-doctor-runtime-recovery-result.md)
- [V2-K Doctor Runtime Confirmation](docs/v2-k/v2k-doctor-confirmation.md)

## V2-L Public Visits-Service Confirmation

V2-L runs the productized raw LVT/LVTT reducer on Spring PetClinic
visits-service, a second public runtime target. Because no visits-specific
full-P2 artifact was available, the measured comparison is visits baseline vs
the same baseline plus the raw reducer.

Under exploded Boot, `NO_CDS_LOW_DIRTY`, `MALLOC_ARENA_MAX=1`, no CDS/AppCDS,
and no runtime javaagent, V2-C accepted all six runs:

```text
paired wins: 3/3
median PSS delta: -2,012 KB
median Private_Dirty delta: -1,680 KB
median memory.current delta: -1,712,128 bytes
dependency-layer byte delta: -3,515,600 bytes
```

V2-D attributes the result primarily to lower `anonymous_rw` PSS and
NMT-visible metaspace movement, not retained heap objects or class-count
reduction. No full-P2, startup, fat-JAR/CDS, all-PetClinic, or universal Spring
Boot claim is made.

See:

- [V2-L Visits Confirmation](docs/v2-l/v2l-visits-confirmation.md)
- [V2-L V2-C Validation](docs/v2-l/v2l-visits-v2c-validation.md)
- [V2-L V2-D Attribution](docs/v2-l/v2l-visits-v2d-attribution.md)
- [V2-L Final Verdict](docs/v2-l/v2l-visits-final-verdict.md)

## V2-M Reducer Recommendation Engine

V2-M adds a report-only product decision layer for the raw LVT/LVTT reducer. It
normalizes artifact, byte-audit, JAR safety, semantic smoke, V2-C, and V2-D
evidence and returns one admission state:

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

Example:

```powershell
mvn -N jmoa:recommend-reducer `
  '-Djmoa.recommendation.enabled=true' `
  '-Djmoa.recommendation.inputDir=<reports-dir>' `
  '-Djmoa.recommendation.service=<service>' `
  '-Djmoa.recommendation.launchMode=EXPLODED_BOOT_APP' `
  '-Djmoa.recommendation.runtimePolicy=NO_CDS_LOW_DIRTY'
```

Historical replay passes 14/14 audited cases: V2-I, V2-K, and V2-L remain
confirmed in their exact scopes; V2-H remains blocked; pre-runtime Doctor
remains artifact-only; V2-Q application confirmation failure remains blocked;
V2-R/V2-S/V2-T/V2-U application/generated discovery decisions remain
report-only. V2-M/V2-R/V2-S/V2-T/V2-U do not mutate bytecode or add a
performance claim.

See:

- [V2-M Admission States](docs/v2-m/v2m-admission-states.md)
- [V2-M Recommendation Rules](docs/v2-m/v2m-recommendation-rules.md)
- [V2-M Historical Replay](docs/v2-m/v2m-historical-recommendation-replay.md)
- [V2-M Closure Report](docs/v2-m/v2m-closure-report.md)

## V2-N Runtime Policy Recommendation Engine

V2-N adds a report-only runtime-policy decision layer. It registers confirmed
raw-reducer protocols and tells the user whether a requested context is
confirmed, needs a screen, is artifact-only, is diagnostic-only, or is blocked.
It never changes the runtime command, image, artifact, or CDS archive.

```powershell
mvn -N jmoa:recommend-runtime `
  '-Djmoa.runtimeRecommendation.enabled=true' `
  '-Djmoa.runtimeRecommendation.mode=analyze' `
  '-Djmoa.runtimeRecommendation.inputDir=<reports-dir>' `
  '-Djmoa.runtimeRecommendation.registry=docs/v2-n/v2n-runtime-protocol-registry.json' `
  '-Djmoa.runtimeRecommendation.service=<service>' `
  '-Djmoa.runtimeRecommendation.launchMode=EXPLODED_BOOT_APP' `
  '-Djmoa.runtimeRecommendation.runtimePolicy=NO_CDS_LOW_DIRTY' `
  '-Djmoa.runtimeRecommendation.reducerEngine=raw'
```

For CDS, the engine requires the exact registered artifact/archive pair and
runtime mapping proof. A no-CDS candidate compared against a CDS baseline is
blocked as a policy mismatch.

See:

- [V2-N Runtime Protocol Registry](docs/v2-n/v2n-runtime-protocol-registry.md)
- [V2-N Policy Rules](docs/v2-n/v2n-runtime-policy-rules.md)
- [V2-N Historical Replay](docs/v2-n/v2n-runtime-replay.md)
- [V2-N Claim Boundary](docs/v2-n/v2n-claim-boundary.md)
- [V2-N Final Verdict](docs/v2-n/v2n-final-verdict.md)

## V2-O Runtime Policy Automation

V2-O turns the existing V2-N/V2-C/V2-D gates into explicit report-only
workflow helpers. It does not choose a policy, change a runtime command, or
make a performance claim. It standardizes the evidence path:

```text
preflight -> train/prove -> semantic smoke -> paired capture -> V2-C -> V2-D
```

The new `jmoa:runtime-preflight` goal computes SHA-256 for the supplied
artifact and optional CDS archive, then reports whether the next allowed gate
is smoke, screen, confirmation, or an explicit block.

```powershell
mvn -N jmoa:runtime-preflight `
  '-Djmoa.runtimePreflight.enabled=true' `
  '-Djmoa.runtimePreflight.inputDir=<reports-dir>' `
  '-Djmoa.runtimePreflight.artifact=<artifact-path>' `
  '-Djmoa.runtimePreflight.service=<service>' `
  '-Djmoa.runtimePreflight.launchMode=EXPLODED_BOOT_APP' `
  '-Djmoa.runtimePreflight.runtimePolicy=NO_CDS_LOW_DIRTY'
```

PowerShell helpers cover explicit CDS training records, container
materialization proof, semantic smoke, V2-C-native paired captures, and a
V2-C/V2-D confirmation wrapper. They do not run a memory claim by themselves.

See:

- [V2-O Runtime Automation Guide](docs/v2-o/v2o-runtime-automation-guide.md)
- [V2-O Runtime Preflight](docs/v2-o/v2o-preflight.md)
- [V2-O Materialization Proof Helper](docs/v2-o/v2o-materialization-proof-helper.md)
- [V2-O Final Verdict](docs/v2-o/v2o-final-verdict.md)
- [V2-O Closure Report](docs/v2-o/v2o-closure-report.md)

## V2-P Runtime Workflow Productization

V2-P joins the existing report-only and evidence gates into a single strict
workflow record:

```text
recommend reducer -> recommend runtime -> preflight -> materialize -> smoke
-> screen -> confirmation -> V2-C -> V2-D -> reviewed claim-register update
```

The script-first coordinator does not hide a screen as confirmation and does
not declare claims. `CLAIM_ALLOWED` means that existing V2-C and V2-D records
make a narrowly scoped claim eligible for human review.

```powershell
./scripts/run-jmoa-runtime-workflow.ps1 `
  -Mode replay `
  -ReplaySuite docs/v2-p/v2p-workflow-replay-suite.json `
  -OutputDir target/v2p-workflow-replay
```

See:

- [V2-P Golden Workflow](docs/v2-p/v2p-golden-workflow.md)
- [V2-P State Machine](docs/v2-p/v2p-workflow-state-machine.md)
- [V2-P Workflow Script](docs/v2-p/v2p-runtime-workflow-script.md)
- [V2-P Claim Register Guard](docs/v2-p/v2p-claim-register-guard.md)
- [V2-P Final Verdict](docs/v2-p/v2p-final-verdict.md)

## V2-Q Application Metadata Admission

V2-Q adds an opt-in application-class path to the raw LVT/LVTT reducer. Only
ordinary packaged application classes are eligible; generated, proxy, lambda,
and unknown families remain report-only or blocked. Public visits artifact and
semantic gates passed. The first screen failed, one diagnostic rerun passed,
and the fresh 3-pair confirmation failed, so V2-Q makes no runtime-memory
claim. Application-class reduction remains artifact/semantic-only unless a
future target crosses the ROI policy or earns its own confirmation.

See [the V2-Q final verdict](docs/v2-q/v2q-final-verdict.md).

## V2-R Application / Generated ROI Discovery

V2-R is a report-only discovery phase after the V2-Q confirmation failure. It
does not rerun V2-Q and does not enable application/generated mutation. It
formalizes ROI thresholds for application-class raw reduction, ranks generated
and synthetic surfaces, and extends `jmoa:recommend-reducer` with
application/generated classifications.

Current conclusion:

```text
V2-Q visits application reduction: low ROI, confirmation failed
Spring Data / AOT generated families: discovery signals, runtime relevance not proven
proxy/CGLIB/ByteBuddy/Hibernate families: mutation blocked
```

See:

- [V2-R Phase Boundary](docs/v2-r/v2r-phase-open.md)
- [V2-R Application Surface Census](docs/v2-r/v2r-application-surface-census.md)
- [V2-R Candidate Ranking](docs/v2-r/v2r-candidate-ranking.md)
- [V2-R Final Verdict](docs/v2-r/v2r-final-verdict.md)

## V2-U Matched Generated-Family Campaign

V2-U keeps generated-family optimization in evidence-gathering mode. It adds
the startup/warmup/workload lifecycle capture harness, requires the full
artifact/service/runtime/reducer/family/scanner identity tuple, and records the
current customers, visits, and Doctor gaps without admitting a prototype.

See:

- [V2-U Phase Boundary](docs/v2-u/v2u-phase-open.md)
- [V2-U Capture Harness](docs/v2-u/v2u-generated-capture-harness.md)
- [V2-U Lifecycle Reconciliation](docs/v2-u/v2u-lifecycle-reconciliation.md)
- [V2-U Prototype Admission](docs/v2-u/v2u-prototype-admission.md)
- [V2-U Final Verdict](docs/v2-u/v2u-final-verdict.md)

## V2-V Fresh Matched Generated-Family Capture Campaign

V2-V makes the V2-U campaign executable. It adds capture preflight, a
standalone raw-stage attribution goal, strict bundle validation, lifecycle and
cross-service family matrices, matched-only ROI ranking, and a one-candidate
admission gate.

```powershell
./scripts/capture-generated-lifecycle.ps1 `
  -Service '<service>' -ArtifactPath '<artifact>' -OutputDir '<diagnostic-dir>' `
  -LaunchMode '<launch-mode>' -RuntimePolicy '<runtime-policy>' `
  -ReducerEngine '<engine>' -PidFile '<pid-file>' `
  -WarmupCommand '<warmup>' -WorkloadCommand '<workload>'

./scripts/run-generated-lifecycle-attribution.ps1 `
  -LifecycleManifest '<diagnostic-dir>/generated-lifecycle-manifest.json' `
  -StaticInventory '<generated-class-inventory.json>'

./scripts/validate-generated-capture-bundles.ps1 `
  -CampaignManifest 'docs/v2-v/v2v-campaign-manifest.example.json'
```

The current V2-V closure is `PARTIAL_INFRASTRUCTURE`: no complete fresh
customers, visits, and Doctor D2R bundle is claimed, no generated-family
prototype is admitted, and no runtime claim is added. See the
[V2-V final verdict](docs/v2-v/v2v-final-verdict.md).

## V2-W Matched Capture Execution

V2-W executed the frozen generated-family campaign against exact Customers,
Visits, and Doctor D2R artifacts. All three startup/warmup/workload bundles
reached `MATCHED_DIAGNOSTIC_EVIDENCE`, spanning exploded Boot/no-CDS and
fat-JAR/CDS. No generated-family prototype was admitted: lambda is the existing
v1 domain, Spring Data and compiler-generated surfaces lack a bounded safe
transform, and AOT/proxy families remain safety blocked.

See the [V2-W final verdict](docs/v2-w/v2w-final-verdict.md).

## Safety

Before publishing or tagging a release, run:

```powershell
./scripts/check-publication-safety.ps1
```

This checks for common local paths, secret-like strings, committed binaries,
generated artifacts, and private service markers.

## License

JMOA is released under the [Apache License 2.0](LICENSE).
