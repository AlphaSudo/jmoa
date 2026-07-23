# JMOA

Evidence-driven, build-time JVM footprint optimization for Spring Boot applications.

[![Build](https://github.com/AlphaSudo/jmoa/actions/workflows/build.yml/badge.svg)](https://github.com/AlphaSudo/jmoa/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AlphaSudo/jmoa)](https://github.com/AlphaSudo/jmoa/releases/tag/v2.0.0)
[![License](https://img.shields.io/github/license/AlphaSudo/jmoa)](LICENSE)
![Build JDK](https://img.shields.io/badge/build-JDK%2026-E76F00?logo=openjdk&logoColor=white)

[Architecture](docs/architecture/system-overview.md) |
[Methodology](docs/methodology/measurement-protocol.md) |
[Reproduction](docs/reproduction/petclinic-quickstart.md) |
[Direct Result](docs/product-evidence/jmoa-vs-no-jmoa-matrix.md) |
[V1 to V2](docs/results/v2-three-service-matrix.md) |
[Portfolio](https://github.com/AlphaSudo/jmoa-jvm-optimization-portfolio)

JMOA rewrites admitted lambda and adapter call sites, reduces selected dependency
classfile metadata, materializes optimized bytecode into the real deployment
shape, proves which artifacts the JVM loaded, and validates memory effects with
paired PSS, Private_Dirty, cgroup, NMT, and class-level evidence.

## Direct Product Reconciliation

The direct clean no-JMOA `B0` versus final V2 matrix is currently
`DIRECT_PRODUCT_MATRIX_INCOMPLETE_ENVIRONMENT_BLOCKED`. Doctor retains a
confirmed direct win. PetClinic's frozen campaign stopped before product pairs
after the same B0 artifact failed the noise gate twice. Patient's accepted
artifact comparison remains open.

| Service | Deployment and policy | Direct B0 to V2 result | State |
| --- | --- | ---: | --- |
| Doctor | Fat JAR, artifact-specific application CDS | **-5,809 KB median PSS**, 3/3 wins | Confirmed |
| PetClinic customers | Exploded Boot, `NO_CDS_LOW_DIRTY` | Not measured by the audited campaign | Environment blocked: 8 valid B0 control arms, same-artifact median absolute PSS noise 4.85-5.58 MB |
| Patient | Fat JAR, stock JDK base CDS | +3,290 KB PSS for attempted candidate | Screen used a non-accepted V2 SHA; accepted comparison remains open |

Doctor's result is the only confirmed direct product win. PetClinic's older
screen and replay remain historical evidence, but the latest audited campaign
does not classify V2 as a win or regression: it never admitted V2 after the B0
control failed. Patient's two screens remain valid only for the attempted
`FB4E...` candidate, not the accepted corrected `4CFC...` V2 artifact.

Read the [direct matrix](docs/product-evidence/jmoa-vs-no-jmoa-matrix.md),
[evidence contract](docs/product-evidence/jmoa-vs-no-jmoa-contract.md), and
[runtime-equivalence contract](docs/product-evidence/runtime-equivalence-investigation-contract.md).
The exact PetClinic terminal result is in the
[campaign result](docs/product-evidence/petclinic-performance-campaign-result.md).

Run a full baseline-to-V2 audited scenario with:

```powershell
pwsh ./scripts/run-petclinic-audited-baseline-v2-scenario.ps1 `
  -PetclinicSource <petclinic-source> `
  -ConfigRepository <petclinic-config-repository> `
  -BuildJavaHome <build-jdk> `
  -RuntimeJavaHome <runtime-jdk> `
  -Maven <mvn-command>
```

This emits one exact command ledger containing the baseline launch, auxiliary
services, warmup, every workload response, memory captures, JMOA transform,
V2 launch, and teardown. See the [ledger contract](docs/product-evidence/scenario-command-ledger-contract.md)
and [fresh PetClinic screen](docs/product-evidence/petclinic-fresh-baseline-v2-scenario.md).

For the balanced product gate, first run the campaign fixtures and readiness
check against a signed manifest:

```powershell
pwsh ./scripts/run-campaign-fixtures.ps1 -OutputDirectory target/campaign-fixtures

pwsh ./scripts/run-petclinic-performance-campaign.ps1 `
  -CampaignManifest <signed-campaign-manifest.json> `
  -FixturesReport ./target/campaign-fixtures/campaign-fixtures.json `
  -RunRoot <private-output-root> `
  -DryRun
```

Remove `-DryRun` only after reviewing the readiness report. The full campaign
runs two reversed B0 same-artifact pairs, two reversed V2 same-artifact pairs,
then three balanced B0/V2 pairs. Every measured B0 and V2 arm receives one
chronological Markdown ledger with commands and responses inline, backed by
hashed stage ledgers and raw files. See the
[campaign readiness result](docs/product-evidence/petclinic-campaign-readiness.md)
and [executed campaign result](docs/product-evidence/petclinic-performance-campaign-result.md).

## V1 To V2 Engineering Evolution

This separate matrix compares the accepted V1 artifact with V2 under one frozen
runtime policy per service. Every row has `6/6` valid runs, zero workload
errors, a V2-C `CONFIRMED_WIN` verdict, and V2-D attribution.

| Service | Deployment | Confirmed policy | Median PSS, V1 to V2 | Wins |
| --- | --- | --- | ---: | ---: |
| PetClinic customers | Exploded Boot / `JarLauncher` | `NO_CDS_LOW_DIRTY` | `-6,012 KB` | `2/3` |
| Doctor | Spring Boot fat JAR | Application CDS | `-5,156 KB` | `3/3` |
| Patient | Spring Boot fat JAR | `JDK_BASE_CDS_LOW_DIRTY` | `-8,279 KB` | `3/3` |

Patient is also independently confirmed under `NO_CDS_LOW_DIRTY` at
`-8,903 KB` median PSS. Dynamic Patient application CDS was rejected for the
tested single-replica deployment. Results are protocol-specific: JMOA does not
claim that CDS, no-CDS, fat JARs, exploded Boot, or one allocator policy is
universally optimal.

These medians explain how the product improved after V1. They are not added to
older baseline-to-V1 results and do not replace the direct matrix above.

Read the [engineering-evolution result](docs/results/v2-three-service-matrix.md) or the
[machine-readable audit matrix](docs/v2-final/v2-three-service-memory-matrix.json).

## What V2 Adds

V1 established build-time lambda and adapter optimization. V2 turns that
transformer into an evidence-gated delivery system:

- raw dependency `LocalVariableTable` and `LocalVariableTypeTable` reduction;
- normalized auditing that permits target-attribute changes and rejects
  unexpected classfile changes;
- Spring Boot fat-JAR and exploded-Boot materialization;
- artifact SHA-256, dependency replacement, and runtime-origin proof;
- paired evidence validation for PSS, Private_Dirty, and `memory.current`;
- smaps, NMT, heap, histogram, class, and metaspace attribution;
- reducer and runtime-policy recommendation engines;
- semantic-smoke and confirmation workflow automation;
- generated/proxy family discovery with conservative safety classification.

The Patient V1-to-V2 result is therefore not a replay of an older lambda-only
benchmark. It compares accepted product artifacts after the V2 reducer,
materialization, policy, evidence, and attribution gates.

## Architecture

```text
Artifact analysis
      |
Candidate admission
      |
Build-time transformation
      |
Non-target byte-preservation audit
      |
Deployment materialization
      |
Runtime-origin and policy proof
      |
Semantic workload
      |
Balanced paired confirmation
      |
V2-C evidence validation
      |
V2-D memory attribution
      |
Scoped claim or automatic rejection
```

The Maven plugin owns scanning, admission, transformation, reporting,
recommendation, evidence, and attribution. `jmoa-runtime-lib` supplies the
runtime adapter types referenced by admitted lambda rewrites. PowerShell
automation materializes and verifies the actual Spring Boot deployment rather
than measuring an unproven build directory.

Start with [System Overview](docs/architecture/system-overview.md), then read
[Bytecode Transformation](docs/architecture/bytecode-transformation.md) and
[Materialization and Origin Proof](docs/architecture/materialization-and-origin-proof.md).

## Safety Model

JMOA treats bytecode optimization as an artifact-integrity problem, not just a
transformation problem.

- Final optimized services use no runtime javaagent.
- Mutation is opt-in; discovery and riskier families remain report-only.
- Artifact and replacement JAR identities are recorded with SHA-256.
- The raw reducer audits all non-target classfile structures for equivalence.
- Signed, sealed, and multi-release JARs are skipped by the productized reducer.
- `jmoa-runtime-lib` can be excluded and its identity proven across variants.
- Materialization proves that every intended dependency was replaced.
- Runtime proof verifies mapped CDS archives and loaded artifact origins.
- Semantic workloads fail on health, request, verifier, class-format, or
  linkage errors.
- A single run is diagnostic only. Performance claims require valid paired
  evidence and survive losing pairs; results are never selected by deleting
  valid outliers.

See [Reducer Configuration](docs/reference/reducer-configuration.md) for the
exact mutation boundary.

## Runtime Policy Is Part of the Artifact Contract

JMOA records runtime policy with the artifact and launch shape:

| Service | Accepted policy | Important distinction |
| --- | --- | --- |
| PetClinic customers | no-CDS | Sharing is explicitly disabled. |
| Doctor | application CDS | Each artifact uses its own trained application archive. |
| Patient | stock JDK base CDS | Both arms map the same JDK archive; no Patient application archive is present. |

On JDK 26 the stock base archive may be used unless sharing is disabled.
Application CDS adds an artifact-specific application archive over that base.
Those are different policies and require different live proof. JMOA therefore
does not reduce policy to a loose "CDS on/off" label.

See [Runtime Policy Model](docs/architecture/runtime-policy-model.md) and
[CDS and Runtime Policy](docs/methodology/cds-and-runtime-policy.md).

## Public PetClinic Workflow

The public customers-service workflow is clean-clone-qualified for build,
raw reduction, exploded-Boot materialization, hash proof, and Java 17 semantic
smoke. The accepted memory result comes from the separate three-pair protocol.

```powershell
git clone https://github.com/AlphaSudo/jmoa.git
cd jmoa
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean install
./examples/spring-petclinic-customers-nocds/scripts/00-quickstart.ps1 -ProfilePath <profile.json> -AdmissionPath <admission.txt> -SafeSamsPath <safe-sams.txt> -RuntimeJar ./jmoa-runtime-lib/target/jmoa-runtime-lib-2.0.0-rc2.jar
```

The quickstart requires the frozen public profile, admission list, and SAM
allowlist used by the qualified workflow. They are not currently attached to
the `v2.0.0` GitHub release, so this is not presented as a zero-input command.
See [PetClinic Quickstart](docs/reproduction/petclinic-quickstart.md) for the
prerequisite contract, [Extended Confirmation](docs/reproduction/extended-confirmation.md)
for the claim protocol, and [Interpreting Results](docs/reproduction/interpreting-results.md)
for verdict rules.

## Results JMOA Rejected

Negative results are release inputs, not discarded experiments:

- the hardened ASM metadata reducer was artifact-safe but failed runtime
  promotion on the accepted PetClinic protocol;
- application-class reduction was artifact-safe but failed runtime
  confirmation;
- generated-family matched evidence found no safe bounded mutation candidate;
- Patient dynamic AppCDS failed the frozen single-replica archive-economics
  gate even though stock base CDS later passed.

The [Negative Results Register](docs/results/negative-results.md) records the
hypothesis, observed artifact result, runtime result, and final disposition.

## Shipped Scope

- build-time lambda and adapter transformation;
- raw dependency LVT/LVTT reduction;
- exploded-Boot and fat-JAR materialization paths;
- no-CDS, stock base-CDS, and confirmed application-CDS policy proof;
- evidence validation and memory attribution engines;
- reducer and runtime-policy recommendations;
- generated-family inventory and report-only relevance analysis.

## Not Claimed

- universal memory or startup improvement;
- generated, proxy, CGLIB, ByteBuddy, Hibernate, or Spring AOT mutation;
- large-method splitting, constant-pool rewriting, or BootstrapMethods rewriting;
- universal CDS benefit;
- automatic mutation of a production deployment;
- transfer of a service result to another launch mode or runtime policy.

## Build And Test

The current CI build uses Temurin 26 and Maven on `ubuntu-latest`. The plugin is
compiled with `--release 22`; the runtime library is compiled with `--release
17`. The public PetClinic semantic smoke exercises the materialized runtime on
Java 17, while the final private confirmation matrix used the recorded Java 26
container protocols.

```powershell
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean test
./scripts/check-documentation-quality.ps1
./scripts/check-publication-safety.ps1
```

See the [Compatibility Matrix](docs/reference/compatibility-matrix.md) for the
tested boundary and the [Maven Goal Reference](docs/reference/maven-plugin-goals.md)
for plugin entry points.

## Repository Map

| Path | Purpose |
| --- | --- |
| `jmoa-maven-plugin/` | Transformation, reducer, evidence, attribution, and recommendation goals |
| `jmoa-runtime-lib/` | Java 17-compatible runtime adapters |
| `scripts/` | Materialization, runtime proof, semantic smoke, and confirmation automation |
| `examples/` | Public PetClinic build and smoke workflow |
| `docs/architecture/` | Product and bytecode architecture |
| `docs/methodology/` | Measurement, statistics, attribution, and policy rules |
| `docs/results/` | Authoritative success and rejection summaries |
| `docs/reference/` | Goals, configuration, schemas, and compatibility |
| `docs/history/` | Phase-indexed provenance and audit links |
| `docs/v2-final/` | Frozen machine-readable release evidence |

The separate [JMOA portfolio](https://github.com/AlphaSudo/jmoa-jvm-optimization-portfolio)
is the hiring and case-study narrative. This repository is the source,
invariant, tooling, and reproduction record. JMOA is licensed under Apache 2.0;
private Patient and Doctor source, configuration, and raw evidence are not
published.
