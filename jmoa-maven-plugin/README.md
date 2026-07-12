# JMOA Maven Plugin

The Maven plugin is the build-time optimizer entry point.

Current responsibilities:

- scan class roots and expanded dependency jars for lambda sites
- apply profile and framework admission rules
- generate Tier 1 runtime plans and Tier 2 package/SAM adapters
- rewrite eligible `invokedynamic` call sites
- package optimized dependency jars
- validate adapter references
- write measurement and class-load evidence summaries
- write V2-A generated/synthetic/proxy/AOT inventory reports when explicitly
  enabled
- write V2-B bytecode/classfile size reports when explicitly enabled
- write V2-C evidence validation reports for already-captured measurements
- write V2-D memory attribution reports for V2-C-valid evidence
- write V2-U matched generated-family lifecycle diagnostics when explicitly
  enabled

The plugin is intentionally not a runtime javaagent. Optimized artifacts are
produced at build time and then verified in the target deployment shape.

## Important Goals

- `jmoa:optimize`
- `jmoa:coverage-report`
- `jmoa:check-coverage`
- `jmoa:measure-impact`
- `jmoa:evidence`
- `jmoa:attribution`
- `jmoa:reduce-bytecode`
- `jmoa:analyze-generated-relevance`
- `jmoa:analyze-generated-evidence`

## V2-A Generated-Class Flags

Generated-class inventory is disabled by default.

```text
jmoa.synthetic.enabled=false
jmoa.synthetic.inventoryOnly=true
jmoa.synthetic.optimizeFamily=none
jmoa.synthetic.failOnUnsafe=true
```

When enabled, the plugin writes inventory, safety taxonomy, prototype selection,
and ROI V2 feature reports under `target/`. Runtime attribution reports are also
written when `jmoa.synthetic.classLoadLog` or `jmoa.synthetic.classHistogram`
points to existing evidence files.

Generated-class bytecode mutation remains disabled. Optimization requests still
fail fast unless the mode is inventory/report-only.

## V2-B Bytecode Size Flags

Bytecode-size profiling is disabled by default.

```text
jmoa.size.enabled=false
jmoa.size.reportOnly=true
jmoa.size.optimize=false
jmoa.size.failOnNear64k=false
jmoa.size.warnMethodBytes=32768
jmoa.size.dangerMethodBytes=49152
jmoa.size.failMethodBytes=65535
```

When enabled, the plugin writes classfile, method, constant-pool, attribute, and
bytecode ROI reports under `target/`. Mutation and strip flags fail fast in this
release.

## V2-C Evidence Flags

Evidence analysis is disabled by default and operates on already-captured run
folders.

```text
jmoa.evidence.enabled=false
jmoa.evidence.mode=analyze
jmoa.evidence.inputDir=<evidence-dir>
jmoa.evidence.outputDir=<output-dir>
jmoa.evidence.expectedPolicy=UNKNOWN
```

The evidence goal validates runs, analyzes paired confirmations, detects
diagnostic perturbation, and emits JSON/Markdown reports. It does not start
containers or change optimizer behavior.

## V2-D Attribution Flags

Memory attribution is disabled by default and requires V2-C-valid evidence by
default.

```text
jmoa.attribution.enabled=false
jmoa.attribution.mode=analyze
jmoa.attribution.inputDir=<v2-c-evidence-dir>
jmoa.attribution.outputDir=<output-dir>
jmoa.attribution.requireV2CValid=true
jmoa.attribution.generatedClassReport=<optional-v2a-report>
jmoa.attribution.bytecodeRuntimeCorrelationReport=<optional-v2b-report>
```

The attribution goal explains memory movement across smaps, NMT, heap/object
histograms, class/metaspace signals, optional V2-A generated-family context, and
optional V2-B bytecode runtime correlation. It is report-only and does not make
V2-A or V2-B mutation safe by itself.

## V2-E Reducer Flags

The bytecode reducer is disabled by default and report-only by default.

```text
jmoa.reducer.enabled=false
jmoa.reducer.reportOnly=true
jmoa.reducer.optimize=false
jmoa.reducer.profile=none
jmoa.reducer.engine=asm
jmoa.reducer.inputDir=<optimized-lib-dir>
jmoa.reducer.outputDir=${project.build.directory}/jmoa-reduced-libs
jmoa.reducer.stripLocalVariableTable=false
jmoa.reducer.stripLocalVariableTypeTable=false
```

Unsafe strip flags fail fast:

```text
jmoa.reducer.stripLineNumberTable=false
jmoa.reducer.stripSourceFile=false
jmoa.reducer.stripStackMapTable=false
jmoa.reducer.stripAnnotations=false
jmoa.reducer.stripSignature=false
jmoa.reducer.stripBootstrapMethods=false
```

Mutation is allowed only when `reportOnly=false`, `optimize=true`,
`profile=release-low-footprint`, and both local-variable strip flags are true.
V2-E preserves line numbers, stack-map frames, annotations, signatures, and
BootstrapMethods. The default `asm` engine skips classes that carry
`BootstrapMethods` in mutation mode instead of rewriting them.

V2-I adds an explicit `raw` engine:

```text
jmoa.reducer.engine=raw
```

The raw engine rewrites only method `Code` attributes to remove
`LocalVariableTable` and `LocalVariableTypeTable`. It preserves BootstrapMethods
while still keeping signed, multi-release, and sealed JAR skips from V2-F. The
raw engine is opt-in and remains under the same release-low-footprint mutation
gate.

V2-F hardens this reducer for product use. Signed, multi-release, and sealed
JARs are skipped by default; `module-info.class` is preserved; and the reducer
emits `jmoa-reducer-manifest.json` with input/output hashes and timestamp
policy.

V2-J adds raw-engine productization outputs:

```text
raw-reducer-byte-preservation-report.json
raw-reducer-byte-preservation-report.md
jmoa-reducer-manifest-v2.json
```

For raw-reduced classes, the auditor normalizes original and reduced class bytes
by removing only `LocalVariableTable` and `LocalVariableTypeTable`; the
normalized byte streams must match exactly. Any non-target byte drift hard-fails
the reducer.

V2-L closes as the second public runtime confirmation for the raw engine. Its
official closure type is `CLOSED_CONFIRMED`; the descriptive phase label is
`CLOSED_CONFIRMED_PUBLIC_SECOND_RUNTIME`. The visits-service claim remains
baseline vs baseline plus raw reducer under its documented exploded-Boot/no-CDS
protocol.

V2-Q adds an explicit application-class admission path:

```text
jmoa.reducer.includeApplicationClasses=true
jmoa.reducer.applicationInputDir=<packaged-classes-directory>
jmoa.reducer.generatedFamilies=report-only
```

It is raw-engine-only and requires the existing explicit mutation flags. Only
ordinary packaged application classes can be reduced; generated/proxy/lambda
families are copied unchanged or blocked. Application output is written in a
parallel `application-classes` tree and must be materialized into
`BOOT-INF/classes`, never an exploded layer root.

Application-class reduction is not promoted like dependency-jar reduction by
default. V2-Q's public visits run removed only `480` application-class bytes;
after one diagnostic screen rerun, the fresh 3-pair confirmation failed. The
recommendation engine therefore treats low-ROI application-class evidence as
artifact/semantic-only unless removable metadata reaches `32 KB`, at least `50`
application classes are reduced, or a service-specific runtime screen passes.

V2-R/V2-S/V2-T/V2-U extend this as report-only ROI discovery,
generated-family runtime relevance, SHA-gated static/runtime reconciliation, and
matched lifecycle evidence campaign support for application/generated surfaces.
The recommendation goal may now emit:

```text
APPLICATION_LOW_ROI_ARTIFACT_ONLY
APPLICATION_SCREEN_REQUIRED
GENERATED_REPORT_ONLY
GENERATED_MUTATION_BLOCKED
CANDIDATE_FOR_PROTOTYPE
```

These decisions classify candidate surfaces only. They do not enable
generated-class mutation, proxy mutation, application-class mutation, or a
runtime claim.

## V2-U Generated-Family Evidence Flags

Generated-family matched-evidence analysis is disabled by default and is
diagnostic-only. It reconciles a V2-A generated-class inventory with
startup/warmup/workload runtime-attribution reports only when the static and
capture evidence share a complete identity tuple.

```text
jmoa.generatedEvidence.enabled=false
jmoa.generatedEvidence.inventory=<generated-class-inventory.json>
jmoa.generatedEvidence.lifecycleManifest=<generated-lifecycle-manifest.json>
jmoa.generatedEvidence.staticIdentity=<optional-static-identity.json>
jmoa.generatedEvidence.outputDir=<output-dir>
```

Identity fields can be supplied through the lifecycle manifest, an optional
static identity file, or explicit Maven properties:

```text
jmoa.generatedEvidence.staticArtifactSha256=<sha256>
jmoa.generatedEvidence.captureArtifactSha256=<sha256>
jmoa.generatedEvidence.service=<service>
jmoa.generatedEvidence.launchMode=<launch-mode>
jmoa.generatedEvidence.runtimePolicy=<runtime-policy>
jmoa.generatedEvidence.reducerEngine=<engine>
jmoa.generatedEvidence.familyRegistryVersion=<version>
jmoa.generatedEvidence.scannerVersion=<version>
```

When `lifecycleManifest` is provided, the goal discovers stage reports from:

```text
startup/generated-class-runtime-attribution.json
warmup/generated-class-runtime-attribution.json
workload/generated-class-runtime-attribution.json
```

The analyzer reports explicit non-admission statuses such as
`ARTIFACT_FINGERPRINT_MISSING`, `ARTIFACT_FINGERPRINT_MISMATCH`,
`SERVICE_MISMATCH`, `REGISTRY_VERSION_MISMATCH`, and
`LIFECYCLE_CAPTURE_INCOMPLETE`. V2-U emits both V2-U and legacy V2-T report
filenames for compatibility, but generated-family mutation remains disabled.

## V2-M Reducer Recommendation Flags

Reducer recommendation is disabled by default and never mutates bytecode.

```text
jmoa.recommendation.enabled=false
jmoa.recommendation.mode=analyze
jmoa.recommendation.inputDir=<reports-dir>
jmoa.recommendation.outputDir=<output-dir>
jmoa.recommendation.service=<service>
jmoa.recommendation.launchMode=<mode>
jmoa.recommendation.runtimePolicy=<policy>
jmoa.recommendation.confirmationScope=UNKNOWN
```

The plugin module requires JDK 22 or newer. On Windows, quote each dotted
Maven property so PowerShell passes it as one literal argument.

Run analyze mode from a multi-module repository root with:

```powershell
mvn -N jmoa:recommend-reducer `
  '-Djmoa.recommendation.enabled=true' `
  '-Djmoa.recommendation.inputDir=<reports-dir>' `
  '-Djmoa.recommendation.service=<service>' `
  '-Djmoa.recommendation.launchMode=EXPLODED_BOOT_APP' `
  '-Djmoa.recommendation.runtimePolicy=NO_CDS_LOW_DIRTY' `
  '-Djmoa.recommendation.confirmationScope=PUBLIC'
```

The input directory may contain a normalized `reducer-admission-input.json` or
the canonical reducer/audit/safety/semantic/V2-C/V2-D reports. Outputs:

```text
reducer-admission-input.json
jmoa-reducer-recommendation.json
jmoa-reducer-recommendation.md
```

Replay mode validates historical admission decisions and fails on mismatches by
default:

```powershell
mvn -N jmoa:recommend-reducer `
  '-Djmoa.recommendation.enabled=true' `
  '-Djmoa.recommendation.mode=replay' `
  '-Djmoa.recommendation.inputDir=../docs/v2-m' `
  '-Djmoa.recommendation.replaySuite=../docs/v2-m/historical-recommendation-suite.json'
```

V2-M/V2-R/V2-S/V2-T/V2-U recommendation and generated-family discovery remain
report-only. `RECOMMEND_CONFIRMED` applies only to an exact confirmed service,
launch mode, and runtime policy; discovery decisions such as
`CANDIDATE_FOR_PROTOTYPE` still require a future phase with semantic, V2-C, and
V2-D gates before mutation or runtime promotion.

See:

- [V2-M Admission States](../docs/v2-m/v2m-admission-states.md)
- [V2-M Recommendation Rules](../docs/v2-m/v2m-recommendation-rules.md)
- [V2-M Historical Replay](../docs/v2-m/v2m-historical-recommendation-replay.md)
- [V2-M Closure Report](../docs/v2-m/v2m-closure-report.md)

## V2-N Runtime Policy Recommendation Flags

Runtime-policy recommendation is disabled by default and never changes
deployment configuration, bytecode, or CDS archives.

```text
jmoa.runtimeRecommendation.enabled=false
jmoa.runtimeRecommendation.mode=analyze
jmoa.runtimeRecommendation.inputDir=<reports-dir>
jmoa.runtimeRecommendation.outputDir=<output-dir>
jmoa.runtimeRecommendation.registry=<runtime-protocol-registry.json>
jmoa.runtimeRecommendation.service=<service>
jmoa.runtimeRecommendation.launchMode=<mode>
jmoa.runtimeRecommendation.runtimePolicy=<policy>
jmoa.runtimeRecommendation.reducerEngine=raw
jmoa.runtimeRecommendation.artifactSha256=<sha optional for no-CDS analysis>
jmoa.runtimeRecommendation.cdsArchiveSha256=<sha required for CDS>
jmoa.runtimeRecommendation.scope=UNKNOWN
```

The plugin module requires JDK 22 or newer. On Windows, quote each dotted
Maven property so PowerShell passes it as one literal argument.

Analyze a known policy with:

```powershell
mvn -N jmoa:recommend-runtime `
  '-Djmoa.runtimeRecommendation.enabled=true' `
  '-Djmoa.runtimeRecommendation.mode=analyze' `
  '-Djmoa.runtimeRecommendation.inputDir=<reports-dir>' `
  '-Djmoa.runtimeRecommendation.registry=../docs/v2-n/v2n-runtime-protocol-registry.json' `
  '-Djmoa.runtimeRecommendation.service=<service>' `
  '-Djmoa.runtimeRecommendation.launchMode=EXPLODED_BOOT_APP' `
  '-Djmoa.runtimeRecommendation.runtimePolicy=NO_CDS_LOW_DIRTY' `
  '-Djmoa.runtimeRecommendation.reducerEngine=raw'
```

Preflight exposes missing gates without changing deployment:

```powershell
mvn -N jmoa:recommend-runtime `
  '-Djmoa.runtimeRecommendation.enabled=true' `
  '-Djmoa.runtimeRecommendation.mode=preflight' `
  '-Djmoa.runtimeRecommendation.inputDir=<reports-dir>' `
  '-Djmoa.runtimeRecommendation.registry=../docs/v2-n/v2n-runtime-protocol-registry.json'
```

Replay mode validates the policy history and fails on mismatches by default:

```powershell
mvn -N jmoa:recommend-runtime `
  '-Djmoa.runtimeRecommendation.enabled=true' `
  '-Djmoa.runtimeRecommendation.mode=replay' `
  '-Djmoa.runtimeRecommendation.inputDir=../docs/v2-n' `
  '-Djmoa.runtimeRecommendation.registry=../docs/v2-n/v2n-runtime-protocol-registry.json' `
  '-Djmoa.runtimeRecommendation.replaySuite=../docs/v2-n/historical-runtime-policy-suite.json'
```

Outputs:

```text
runtime-policy-admission-input.json
jmoa-runtime-recommendation.json
jmoa-runtime-recommendation.md
```

See:

- [V2-N Runtime Protocol Registry](../docs/v2-n/v2n-runtime-protocol-registry.md)
- [V2-N Policy Rules](../docs/v2-n/v2n-runtime-policy-rules.md)
- [V2-N Runtime Preflight](../docs/v2-n/v2n-runtime-preflight.md)
- [V2-N Historical Replay](../docs/v2-n/v2n-runtime-replay.md)
- [V2-N Closure Report](../docs/v2-n/v2n-closure-report.md)

## V2-O Runtime Policy Automation

V2-O adds the workflow helpers around V2-N. The new preflight goal calculates
the supplied artifact and optional CDS archive SHA-256 values before it applies
the existing policy rules:

```powershell
mvn -N jmoa:runtime-preflight `
  '-Djmoa.runtimePreflight.enabled=true' `
  '-Djmoa.runtimePreflight.inputDir=<reports-dir>' `
  '-Djmoa.runtimePreflight.outputDir=<output-dir>' `
  '-Djmoa.runtimePreflight.registry=../docs/v2-n/v2n-runtime-protocol-registry.json' `
  '-Djmoa.runtimePreflight.artifact=<artifact-path>' `
  '-Djmoa.runtimePreflight.service=<service>' `
  '-Djmoa.runtimePreflight.launchMode=EXPLODED_BOOT_APP' `
  '-Djmoa.runtimePreflight.runtimePolicy=NO_CDS_LOW_DIRTY' `
  '-Djmoa.runtimePreflight.reducerEngine=raw'
```

For CDS, include `-Djmoa.runtimePreflight.cdsArchive=<fresh-archive-path>`.
The report is a readiness gate only: it does not train CDS, alter a command, or
make a runtime claim.

The companion PowerShell helpers under `../scripts` standardize explicit CDS
training records, materialization proof, semantic smoke, V2-C-native paired
capture, and V2-C/V2-D confirmation orchestration.

See [V2-O Runtime Automation Guide](../docs/v2-o/v2o-runtime-automation-guide.md).

## V2-P Runtime Workflow Coordinator

V2-P is intentionally script-first. Use
`../scripts/run-jmoa-runtime-workflow.ps1` to normalize existing V2-M, V2-N,
V2-O, V2-C, and V2-D reports into one workflow state. The script can execute
only explicitly configured existing V2-O steps; it does not add a Maven goal,
mutate artifacts, or declare claims.

Run the state-machine replay with:

```powershell
../scripts/run-jmoa-runtime-workflow.ps1 `
  -Mode replay `
  -ReplaySuite ../docs/v2-p/v2p-workflow-replay-suite.json `
  -OutputDir target/v2p-workflow-replay
```

Use `../scripts/check-claim-register-consistency.ps1` after a reviewed workflow
report. `CLAIM_ALLOWED` remains an eligibility result; the claim register is
never edited automatically.

See the repository-level docs for deployment materialization and measurement
boundaries.
