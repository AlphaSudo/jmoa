# JMOA

Evidence-driven, build-time JVM footprint optimization for Spring Boot applications.

[![Build](https://github.com/AlphaSudo/jmoa/actions/workflows/build.yml/badge.svg)](https://github.com/AlphaSudo/jmoa/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AlphaSudo/jmoa)](https://github.com/AlphaSudo/jmoa/releases/tag/v2.0.0)
[![License](https://img.shields.io/github/license/AlphaSudo/jmoa)](LICENSE)
![Build JDK](https://img.shields.io/badge/build-JDK%2026-E76F00?logo=openjdk&logoColor=white)

[Architecture](docs/architecture/system-overview.md) |
[Methodology](docs/methodology/measurement-protocol.md) |
[Reproduction](docs/reproduction/petclinic-quickstart.md) |
[Direct Result](docs/product-evidence/b0-v1-v2-three-service-matrix.md) |
[Forensic Matrix](docs/product-evidence/b0-v1-v2-final-forensic-matrix.md) |
[V1 to V2](docs/results/v2-three-service-matrix.md) |
[Portfolio](https://github.com/AlphaSudo/jmoa-jvm-optimization-portfolio)

JMOA rewrites admitted lambda and adapter call sites, reduces selected dependency
classfile metadata, materializes optimized bytecode into the real deployment
shape, proves which artifacts the JVM loaded, and validates memory effects with
paired PSS, Private_Dirty, cgroup, NMT, and class-level evidence.

## Direct B0 To V2 Product Matrix

The final direct campaign compares strict no-JMOA B0, accepted V1, and accepted
V2 under one frozen runtime per service. Each service completed three valid
qualification observations followed by all six B0/V1/V2 order permutations:
`18/18` final observations and zero semantic errors.

| Service | Deployment and policy | B0 to V2 median PSS | Wins | 95% bootstrap CI | Product gate |
| --- | --- | ---: | ---: | ---: | --- |
| Doctor | Fat JAR, application CDS | **-4,715.5 KB** | 5/6 | [-7,107, -268.5] KB | Passed |
| Patient | Fat JAR, stock JDK base CDS | -1,266.5 KB | 4/6 | [-12,330.5, 3,488.5] KB | Not passed |
| PetClinic customers | Exploded Boot, `NO_CDS_LOW_DIRTY` | -2,947 KB | 4/6 | [-6,516.5, 4,215] KB | Not passed |

The frozen three-service launch criterion is therefore **not passed**: one of
three services is a complete product win. Patient and PetClinic show direct
memory reductions but miss the required `-4,096 KB` PSS magnitude and
bootstrap-upper-below-zero gates. Patient's row comes from the one corrected
campaign authorized by a proven capture-timing defect; it is still a valid
non-win. These observations are retained; no valid losing run was replaced.

Read the [direct matrix](docs/product-evidence/b0-v1-v2-three-service-matrix.md),
[forensic matrix](docs/product-evidence/b0-v1-v2-final-forensic-matrix.md),
[V1 runtime-cost census](docs/product-evidence/v1-runtime-cost-census.md),
[campaign seal](docs/product-evidence/three-artifact-campaign-seal.md),
[balanced protocol](docs/product-evidence/b0-v1-v2-balanced-protocol.md), and
the [adoption/evaluation guide](docs/adoption/README.md).

The current engineering diagnosis is that V1 has a positive median PSS cost in
all three unified campaigns, while V2's metadata reduction more than offsets
that cost in median direct results. Existing captures prove artifact admission
and shared JMOA runtime-family objects, but not exact transformed-site
execution. Choose between the [reducer-only B0R path and full
optimization](docs/adoption/14-choose-jmoa-mode.md) explicitly; do not infer a
universal full-pipeline recommendation from incremental V1-to-V2 wins.

Run the same workflow with a private frozen service config:

```powershell
pwsh ./scripts/run-jmoa-evaluation.ps1 `
  -Service PetClinicCustomers `
  -ConfigPath <private-campaign-config.json> `
  -OutputDirectory <private-output-root> `
  -DryRun
```

Remove `-DryRun` after reviewing the artifact and implementation freeze. Each
qualification and final observation gets a complete chronological Markdown
ledger containing launch commands, support-service logs, warmup and workload
responses, captures, and teardown. Raw evidence and private configuration stay
outside the repository.

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
