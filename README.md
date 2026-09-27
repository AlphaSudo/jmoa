# JMOA 2.1

**Evidence-gated, build-time JVM footprint optimization for Spring Boot.**

[![Build](https://github.com/AlphaSudo/jmoa/actions/workflows/build.yml/badge.svg)](https://github.com/AlphaSudo/jmoa/actions/workflows/build.yml)
[![Release](https://img.shields.io/github/v/release/AlphaSudo/jmoa)](https://github.com/AlphaSudo/jmoa/releases/tag/v2.1.0)
[![License](https://img.shields.io/github/license/AlphaSudo/jmoa)](LICENSE)
![Runtime JDK](https://img.shields.io/badge/runtime-Java%2017%2B-E76F00?logo=openjdk&logoColor=white)
![Claim](https://img.shields.io/badge/PetClinic-claimable%20RAM%20win-1f883d)

[Technical paper](docs/paper/jmoa-v2.1-petclinic-memory-engineering.md) ·
[PetClinic result](docs/product-evidence/petclinic-r41f-b0-t7r23-result.md) ·
[Architecture](docs/architecture/system-overview.md) ·
[Methodology](docs/methodology/measurement-protocol.md) ·
[Quickstart](docs/reproduction/petclinic-quickstart.md) ·
[Engineering portfolio](https://github.com/AlphaSudo/jmoa-jvm-optimization-portfolio)

JMOA treats JVM memory reduction as a delivery problem, not a bytecode trick.
It profiles real execution, admits only bounded transformations, rewrites at
build time, materializes the actual Spring Boot deployment, proves what the JVM
loaded, and accepts a result only after paired process- and cgroup-memory
evidence passes frozen gates.

## PetClinic result: 14.88 MiB less process PSS

JMOA 2.1 publishes one current product claim: the exact accepted **R41F JMOA
fat-JAR deployment** versus the documented strict **no-JMOA B0 exploded-Boot
deployment** of Spring PetClinic's customers service.

| Held-out metric, R41F minus B0E | Result | Evidence |
| --- | ---: | --- |
| Process PSS | **-15,241.5 KiB (-14.88 MiB)** | 12/12 favorable; 95% bootstrap CI [-16,109.5, -15,052.5] KiB |
| `memory.current` | **-17,033,216 bytes (-16.24 MiB)** | 12/12 favorable; 95% CI [-17,842,176, -16,713,728] bytes |
| Private Dirty | **-15,252 KiB** | 12/12 favorable; 95% CI [-16,062, -14,880] KiB |
| Cgroup anonymous memory | **-15,616,000 bytes** | 12/12 favorable |
| Cgroup file memory | **-411,648 bytes** | 12/12 favorable |
| Cgroup kernel memory | **-1,079,296 bytes** | 12/12 favorable |
| Exact paired sign test | **p = 0.00048828125** | Frozen primary inference |

The campaign completed **81/81 sessions**: five qualification observations,
20 same-artifact controls, eight screening observations, and 48 held-out
four-arm observations. No predecessor observation was reused. All RAM,
uncertainty, semantic, lifecycle, and product-cost gates passed, and the host
was restored after execution.

The result includes measured costs: median lifecycle CPU increased **14.71%**
and median startup increased **1.652 s (3.76%)**. Median request latency and
p95 latency changed by **0 ms**. These costs passed their prospectively frozen
limits; they are not hidden from the claim.

This is a **packaging-inclusive deployment result**. It is not a claim that
JMOA content alone saves 14.88 MiB, that exploded Boot is universally worse,
or that every Spring service will reproduce the same effect. The four-arm
factorial measured packaging as the dominant main effect in this deployment.
That boundary is part of the result, not a footnote.

Read the [human result](docs/product-evidence/petclinic-r41f-b0-t7r23-result.md),
the [machine-readable record](docs/product-evidence/petclinic-r41f-b0-t7r23-result.json),
or the [full technical paper](docs/paper/jmoa-v2.1-petclinic-memory-engineering.md).

## The problem JMOA solves

Optimizing a JVM service is harder than deleting a few classfile bytes:

- a transformed class can be valid yet never execute in production;
- an optimized dependency can be built correctly but omitted from the final
  Spring Boot artifact;
- a smaller JAR can consume more RAM after class loading, JIT compilation,
  allocation, and page residency;
- CDS, packaging, allocator state, and startup order can move memory by more
  than the proposed optimization;
- an attractive RSS snapshot can disappear under a paired cold-start campaign.

JMOA connects those layers. It answers both questions an optimization project
must answer: **what can be changed safely?** and **did the deployed process
actually use less memory?**

## How it works

```text
representative workload
        │
        ▼
profile lambda and runtime behavior
        │
        ▼
admit safe, useful transformation candidates
        │
        ├── build-time lambda/adapter rewriting
        └── audited dependency LVT/LVTT reduction
        │
        ▼
byte-preservation and reproducibility audit
        │
        ▼
materialize the real fat-JAR or exploded-Boot deployment
        │
        ▼
prove artifact hashes, replacement, runtime origins, and JVM policy
        │
        ▼
run semantic workload + paired PSS/cgroup confirmation
        │
        ▼
publish a scoped claim — or reject the candidate
```

### 1. Profile and admit

The training agent records stable lambda-site identities and invocation counts.
The Maven plugin combines that profile with explicit safety rules. Unsupported
capturing, serializable, `altMetafactory`, framework-owned, or otherwise risky
sites remain unchanged.

### 2. Transform at build time

Admitted non-capturing lambda and adapter sites are rewritten before deployment.
The production process does not need a JMOA runtime javaagent. The reducer can
also remove selected `LocalVariableTable` and `LocalVariableTypeTable` debug
metadata from eligible dependency classes.

### 3. Audit the artifact

JMOA records classfile component digests and rejects unexplained changes.
Signed, sealed, and multi-release JARs are conservatively excluded from the
productized raw reducer. Reports preserve the reason every candidate was
accepted, rejected, or left report-only.

### 4. Materialize and prove the deployment

JMOA handles Spring Boot fat JARs and exploded layouts as distinct deployment
contracts. It verifies replacement hashes and runtime origins so an experiment
cannot accidentally measure stale or baseline bytecode.

### 5. Measure the whole system

The evidence layer observes process PSS, Private Dirty, `smaps` categories,
cgroup v2 `memory.current`/`memory.stat`, JVM Native Memory Tracking, heap,
metaspace, class counts, faults, CPU, startup, latency, and semantic behavior.
Same-artifact controls qualify the measurement environment before treatment
results are admitted.

## What ships in 2.1

- Maven plugin for profiling-aware lambda/adapter optimization;
- Java 17-compatible runtime adapter library;
- raw dependency LVT/LVTT reducer with non-target byte-preservation auditing;
- Spring Boot fat-JAR and exploded-Boot materialization tooling;
- artifact identity and runtime-origin verification;
- evidence validation, attribution, and recommendation engines;
- PetClinic build/smoke example and the accepted v2.1 evidence record;
- release JARs, source JARs, POMs, manifest, and SHA-256 checksums.

Generated proxies, CGLIB, ByteBuddy, Hibernate, and Spring AOT families remain
inventory/report-only. JMOA 2.1 does not silently mutate them.

## Build and install

JMOA is distributed through GitHub Releases rather than Maven Central.

Build from source:

```bash
git clone https://github.com/AlphaSudo/jmoa.git
cd jmoa
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean install
```

Or download the `v2.1.0` assets and install them into a local Maven repository:

```powershell
gh release download v2.1.0 --repo AlphaSudo/jmoa --dir target/v2-release
pwsh ./scripts/install-v2-release-artifacts.ps1 `
  -ReleaseDir target/v2-release `
  -Version 2.1.0
```

Invoke a plugin goal with:

```powershell
mvn com.yourorg.jmoa:jmoa-maven-plugin:2.1.0:<goal> -D<property>=<value>
```

See the [goal reference](docs/reference/maven-plugin-goals.md) and
[reducer configuration](docs/reference/reducer-configuration.md) before using
mutation goals on a production artifact.

## Public PetClinic workflow

The public customers-service example pins Spring PetClinic source revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967` and demonstrates build, reduction,
materialization, hash proof, and Java 17 semantic smoke:

```powershell
./examples/spring-petclinic-customers-nocds/scripts/00-quickstart.ps1 `
  -ProfilePath <profile.json> `
  -AdmissionPath <admission.txt> `
  -SafeSamsPath <safe-sams.txt> `
  -RuntimeJar ./jmoa-runtime-lib/target/jmoa-runtime-lib-2.1.0.jar
```

The frozen training/admission inputs and the Linux campaign environment remain
explicit prerequisites; the quickstart is not advertised as a zero-input
reproduction of the 81-session memory claim. See
[PetClinic quickstart](docs/reproduction/petclinic-quickstart.md) and
[extended confirmation](docs/reproduction/extended-confirmation.md).

## Safety and claim discipline

- Final optimized services run without a JMOA javaagent.
- Mutation is opt-in; discovery defaults to report-only.
- Every intended artifact replacement is hash-bound.
- Semantic failures, verifier errors, workload errors, swap, teardown failure,
  or invalid controls stop a campaign.
- A single favorable run is diagnostic only.
- Valid losing observations are retained; gates are not relaxed after exposure.
- Service results do not transfer automatically to another service, packaging
  shape, JVM tuple, CDS policy, or workload.

JMOA's long negative-results history is public because rejecting unsafe or
non-economic candidates is part of the product. See the
[negative-results register](docs/results/negative-results.md).

## Repository map

| Path | Purpose |
| --- | --- |
| `jmoa-maven-plugin/` | Transformation, reducer, evidence, attribution, and recommendation goals |
| `jmoa-runtime-lib/` | Java 17-compatible runtime adapters |
| `scripts/` | Materialization, proof, release, smoke, and confirmation automation |
| `examples/` | Public Spring PetClinic workflow |
| `docs/architecture/` | Product and bytecode architecture |
| `docs/methodology/` | Measurement, statistics, attribution, and policy rules |
| `docs/product-evidence/` | Human and machine-readable product claims |
| `docs/paper/` | Expert technical papers |
| `docs/results/` | Accepted and rejected engineering outcomes |

## Scope

JMOA 2.1 is a research/tooling release for engineers who can inspect bytecode,
deployment packaging, and runtime evidence. It does not promise universal RAM
reduction or automatically modify a production service. PetClinic is the first
v2.1 published service example; additional service studies will follow as
independent, service-scoped evidence.

JMOA is licensed under Apache 2.0. The source repository contains public
tooling and sanitized evidence; private service source and raw evidence are not
published.
