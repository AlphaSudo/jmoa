# JMOA 2.1: Evidence-Gated Build-Time JVM Footprint Optimization

**A technical report on bytecode transformation, deployment materialization,
and the Spring PetClinic direct-RAM result**

JMOA Project · AlphaSudo · September 2026

## Abstract

JVM footprint work often fails between a promising transformation and a
defensible production result. A classfile can become smaller while the running
service consumes more memory. A correct transformed dependency can be absent
from the packaged application. A favorable RSS snapshot can be dominated by
page-cache history, allocator retention, JIT timing, garbage-collector state,
or deployment layout. These failure modes are especially important in Spring
Boot services, where application bytecode, nested dependencies, launchers,
class loading, and container accounting interact.

JMOA is an evidence-gated, build-time JVM footprint optimization system. It
combines workload profiling, conservative candidate admission, lambda and
adapter rewriting, selected classfile debug-metadata reduction, byte-preserving
audits, Spring Boot deployment materialization, runtime-origin verification,
and paired Linux memory measurement. Its unit of success is not “bytes removed
from a JAR.” It is an exact, semantically valid deployment whose measured
memory reduction survives same-artifact controls and prospectively frozen
acceptance rules.

JMOA 2.1 publishes its first current service-scoped direct product result. In a
fresh four-arm Spring PetClinic customers-service campaign, exact accepted R41F
reduced median process proportional set size (PSS) by **15,241.5 KiB** and
target-cgroup `memory.current` by **17,033,216 bytes** relative to the
documented strict no-JMOA B0 exploded-Boot deployment. All 12 held-out blocks
favored R41F; the exact paired sign-test p-value was 0.00048828125 and the PSS
bootstrap 95% interval was [-16,109.5, -15,052.5] KiB. Median lifecycle CPU
increased 14.71% and startup increased 1.652 seconds. A four-arm factorial
showed that packaging was the dominant measured main effect, so the public
claim is explicitly packaging-inclusive rather than content-only.

This report describes the engineering problem, JMOA architecture, safety
model, experimental protocol, result, limitations, and practical adoption
boundary.

## 1. The engineering problem

### 1.1 Artifact optimization is not runtime optimization

Classfile size, JAR size, committed JVM memory, resident pages, proportional
set size, and cgroup charge are different quantities. They can move in
different directions.

Removing debug attributes can reduce stored bytes without changing which heap
regions are committed. Replacing `invokedynamic` lambda sites can reduce one
runtime structure while creating another. Repackaging identical logical
content can change read order, page residency, class-loading order, and
garbage-collector state. A complete optimizer must therefore trace the path
from source bytecode to the live deployment rather than stopping at build
output.

### 1.2 Spring Boot creates a deployment identity problem

A Spring Boot service is not merely a directory of `.class` files. It may be
launched from a fat JAR, an exploded Boot tree, a layered container image, or a
custom classpath. Dependencies may be nested, replaced, extracted, reordered,
or accidentally left stale. The runtime can load a class from a location other
than the one an optimizer intended.

JMOA consequently treats deployment shape as part of the artifact contract.
The system records hashes of the transformed outputs, proves dependency
replacement during materialization, and captures runtime origins. A memory
number is inadmissible when artifact identity is ambiguous.

### 1.3 JVM memory measurement has a variance problem

Fresh JVM launches contain real nondeterminism: compilation order, allocation
order, garbage-collection phase, native allocator retention, thread activity,
page faults, and kernel accounting can vary. Host activity, transparent huge
pages, swap, reclaim, and process-capture timing add further movement.

JMOA does not assume a single “quiet” snapshot is representative. It qualifies
the environment with same-artifact controls, freezes the measurement contract
before treatment exposure, records multiple claim frames, preserves all valid
losing observations, and rejects a campaign when lifecycle evidence is
incomplete.

### 1.4 Optimization needs an economic boundary

A memory reduction can be real and still be a poor product choice. CPU,
startup, latency, image size, operational complexity, and compatibility must be
measured beside RAM. JMOA therefore carries explicit product-cost gates. The
PetClinic 2.1 result is reported as a RAM win with CPU and startup tradeoffs,
not as a free improvement.

## 2. Design thesis

JMOA's thesis is that JVM footprint optimization should be a closed evidence
loop:

1. observe representative execution;
2. admit only transformations with explicit safety and relevance evidence;
3. transform before production startup;
4. audit every allowed and unexplained byte change;
5. materialize the deployment actually used by the service;
6. prove artifact and runtime identity;
7. exercise semantics under the target workload;
8. measure process and cgroup memory with qualified controls;
9. publish a narrowly worded claim or reject the candidate.

The build-time decision is only one part of the system. Evidence production and
automatic refusal are product features of equal importance.

## 3. System architecture

```text
             ┌────────────────────────┐
             │ representative workload│
             └───────────┬────────────┘
                         │
                         ▼
             ┌────────────────────────┐
             │ profile + site identity│
             └───────────┬────────────┘
                         │
                         ▼
             ┌────────────────────────┐
             │ conservative admission │
             └───────────┬────────────┘
                         │
             ┌───────────┴───────────┐
             ▼                       ▼
  ┌──────────────────────┐  ┌────────────────────────┐
  │ lambda/adapter rewrite│  │ audited LVT/LVTT reduce│
  └───────────┬──────────┘  └───────────┬────────────┘
              └─────────────┬────────────┘
                            ▼
             ┌──────────────────────────┐
             │ byte-preservation audit  │
             └────────────┬─────────────┘
                          ▼
             ┌──────────────────────────┐
             │ deployment materializer  │
             └────────────┬─────────────┘
                          ▼
             ┌──────────────────────────┐
             │ identity + origin proof  │
             └────────────┬─────────────┘
                          ▼
             ┌──────────────────────────┐
             │ semantic + memory campaign│
             └────────────┬─────────────┘
                          ▼
             scoped claim or rejection
```

### 3.1 Workload profiler

The training agent observes lambda sites under a representative workload and
writes stable site identities with invocation counts. A profile is tied to a
specific bytecode state. Coverage checks detect new sites and missing sites so
a stale profile cannot silently drive a new build.

The agent is a training tool. The final optimized service runs without a JMOA
runtime javaagent; the memory claim therefore belongs to the built artifact and
deployment, not to an active instrumentation layer.

### 3.2 Candidate admission

The plugin combines profile relevance with structural safety rules. The
productized transformation scope is intentionally narrow:

- non-capturing lambda and method-reference sites;
- supported `LambdaMetafactory.metafactory` shapes;
- known SAM interfaces and generated adapter strategies;
- explicitly admitted application or compiled roots.

Capturing lambdas, serializable and `altMetafactory` sites, excluded framework
packages, unsupported SAM categories, and other risky shapes remain unchanged.
Generated proxies and AOT families are inventoried and attributed but remain
report-only in 2.1.

### 3.3 Build-time lambda and adapter rewriting

JMOA can replace admitted lambda/adapter call sites with generated runtime or
package-local adapter paths. Public-access targets and access-restricted
targets use separate strategies. Coverage and rewrite reports reconcile
planned, transformed, excluded, and residual sites.

The point is not to rewrite every lambda. The point is to replace a bounded set
whose structure and workload evidence justify the change while leaving the JVM
to handle everything else normally.

### 3.4 Raw dependency metadata reduction

The reducer can remove `LocalVariableTable` and
`LocalVariableTypeTable` attributes from eligible dependency classes. It
computes classfile component digests and compares non-target components before
and after mutation. Unexpected changes reject the output.

Signed, sealed, and multi-release JARs are excluded by the product safety
policy. Application-class reduction and more invasive classfile rewrites are
not inferred from dependency success; each requires separate admission.

### 3.5 Materialization and runtime-origin proof

JMOA supports both Spring Boot fat-JAR and exploded-Boot deployment paths. The
materializer verifies that every intended dependency was replaced and records
artifact SHA-256 identities. Runtime proof then checks loaded origins and the
active runtime policy.

This layer prevents a common benchmarking error: producing an optimized build
but measuring a stale dependency, different image, or different launcher.

### 3.6 Evidence and attribution

The measurement layer combines:

- process PSS, RSS, Private Clean, and Private Dirty;
- anonymous, file, and shared-memory PSS;
- cgroup v2 `memory.current`, `memory.peak`, and `memory.stat`;
- JVM Native Memory Tracking;
- Java heap, metaspace, code cache, class count, and thread evidence;
- page faults, CPU, startup, request latency, and workload semantics.

No one metric is treated as universal truth. PSS is the primary process
footprint measure; cgroup accounting corroborates the total charged deployment;
mapping- and JVM-level metrics explain where movement occurred.

## 4. Safety and refusal model

JMOA's safety model has four layers.

First, **mutation safety** limits what bytecode can change. Unsupported
structures are excluded rather than guessed.

Second, **artifact integrity** binds outputs to hashes and audits non-target
classfile content.

Third, **semantic validity** requires health, workload, mutation, verifier,
class-format, linkage, and teardown evidence. A memory improvement cannot
compensate for semantic failure.

Fourth, **inference validity** qualifies same-artifact variance and lifecycle
state before a product contrast is accepted. Swap, host discontinuity,
incomplete capture, unstable controls, and failed cleanup stop a campaign.

This refusal behavior is visible in the project history. JMOA retained
artifact-safe but runtime-negative reducer attempts, invalid measurement
epochs, and non-economic runtime policies. Those outcomes influenced the final
protocol rather than being deleted from the narrative.

## 5. PetClinic 2.1 experiment

### 5.1 Research question

The primary question was:

> Under a frozen agent-free, CDS-off Java 17 runtime, does the exact accepted
> R41F JMOA fat-JAR deployment reduce process PSS relative to the documented
> strict no-JMOA B0 exploded-Boot deployment, with corroborating target-cgroup
> RAM and acceptable product cost?

The design deliberately calls this a deployment comparison. It does not assume
the observed delta belongs exclusively to bytecode content.

### 5.2 Four arms

The held-out factorial used four roles:

| Arm | Content | Packaging |
| --- | --- | --- |
| `B0E` | Strict no-JMOA B0 | Exploded Boot |
| `B0F` | Strict no-JMOA B0 | Fat JAR |
| `R41E` | Exact R41 optimized logical content | Exploded Boot |
| `R41F` | Exact R41 optimized logical content | Fat JAR |

The primary product contrast was `R41F - B0E`. The additional arms estimated
content, packaging, and interaction effects without changing the primary
question.

### 5.3 Runtime tuple

The service ran on Eclipse Temurin 17.0.19+10 with Spring Boot 4.0.1, Serial
GC, 32 MiB initial heap, 256 MiB maximum heap, 256 KiB stacks, a 48 MiB code
cache, two compiler threads, Native Memory Tracking summary mode, CDS disabled,
`MALLOC_ARENA_MAX=1`, and a 512 MiB cgroup limit. No JMOA agent was present.

Each session used a 20-second warmup followed by an 81-request semantic
workload paced at 200 ms. The measurement estimator used stopped claim frames
at +10, +15, and +20 seconds. Runtime state, swap, reclaim, workload behavior,
capture integrity, and teardown were sealed with the session.

### 5.4 Campaign structure

The campaign completed 81 sessions:

- five single-arm qualification observations;
- ten same-artifact control pairs, 20 sessions;
- four alternating direct screen pairs, eight sessions;
- twelve held-out four-arm blocks, 48 sessions.

All attempted sessions completed. No predecessor observation was reused and no
replacement observation was required. The held-out schedule varied arm order
across blocks to avoid confounding the treatment with a fixed sequence.

### 5.5 Statistics

Each session reduced three claim frames to its frozen estimator. Each held-out
block then produced paired arm contrasts. The primary report includes the
median block delta, favorable-block count, an exact paired sign test, and a
100,000-sample bootstrap interval using seed 72,104,721.

The inference was frozen before held-out exposure. Same-artifact controls were
authorization gates, not a pool from which favorable controls could be chosen.
The publication pipeline used compare-and-swap updates for both the experiment
registry and accepted-deployment pointer.

## 6. Results

### 6.1 Primary memory result

| Metric | Median R41F - B0E | Favorable | 95% bootstrap interval |
| --- | ---: | ---: | ---: |
| Process PSS | **-15,241.5 KiB** | 12/12 | [-16,109.5, -15,052.5] KiB |
| Private Dirty | **-15,252 KiB** | 12/12 | [-16,062, -14,880] KiB |
| `memory.current` | **-17,033,216 B** | 12/12 | [-17,842,176, -16,713,728] B |
| Cgroup anonymous | **-15,616,000 B** | 12/12 | [-16,445,440, -15,243,264] B |
| Cgroup file | **-411,648 B** | 12/12 | [-450,560, -362,496] B |
| Cgroup kernel | **-1,079,296 B** | 12/12 | [-1,097,728, -1,067,008] B |

The exact sign-test p-value for PSS and each wholly favorable cgroup component
was 0.00048828125. A baseline-residual debit left the PSS median and interval
unchanged. `memory.peak` also improved by a median 19,451,904 bytes, favorable
in 10/12 blocks.

### 6.2 Runtime attribution

Java-heap PSS fell 15,092 KiB while native process-heap PSS increased 2,972
KiB. Anonymous PSS fell 15,250 KiB; file PSS moved only -79 KiB at the process
level. The result is therefore not “every category became smaller.” It is a
total process and cgroup win despite an adverse native-heap component.

### 6.3 Factorial attribution

The packaging main effect was -12,715.25 KiB PSS, the content main effect was
-2,465 KiB, and the interaction was +3,801.5 KiB. The direct baseline
packaging contrast `B0F - B0E` was -14,725.5 KiB. Within fat packaging,
`R41F - B0F` was -979 KiB; it was favorable in 9/12 blocks but did not pass an
independent exact sign-test threshold (`p=0.14599609375`). Within exploded
packaging, `R41E - B0E` was -4,493.5 KiB.

These results matter for interpretation. JMOA produced and selected an exact
accepted deployment that uses less RAM, but the 14.88 MiB direct delta cannot
be marketed as bytecode-content savings alone. Packaging and lifecycle state
are material parts of this deployment result.

### 6.4 Product cost

Median lifecycle CPU increased 4,841,192 microseconds, or 14.7073%. Median
startup increased 1,652 ms, or 3.7611%. Median request latency and p95 request
latency both changed by 0 ms. Loaded classes decreased by 175.

Every frozen cost gate passed. Whether the CPU/RAM exchange is attractive is a
deployment decision: memory-constrained, long-lived replicas may value it more
than short-lived CPU-constrained jobs.

## 7. What the result proves

The experiment supports the following statement:

> Under the frozen agent-free, CDS-off PetClinic protocol, the exact accepted
> R41F JMOA fat-JAR deployment reduced process PSS by 15,241.5 KiB and
> target-cgroup `memory.current` by 17,033,216 bytes versus the documented
> strict no-JMOA B0 exploded-Boot deployment. All 12 held-out blocks favored
> R41F; median lifecycle CPU increased 14.71%. This is a packaging-inclusive,
> service-specific result.

It also proves that the improvement was not created by one favorable PSS
sample: process PSS, Private Dirty, cgroup anonymous memory, cgroup file memory,
and cgroup kernel memory all moved favorably across every held-out block.

## 8. What the result does not prove

The experiment does not prove:

- a universal 15 MiB saving for Spring Boot;
- that metadata removal alone caused the result;
- that fat JARs are always more memory-efficient than exploded Boot;
- that every JMOA transformation is beneficial;
- that another JVM, GC, heap size, allocator, service, or workload will produce
  the same delta;
- that the CPU tradeoff is acceptable for every deployment;
- that report-only proxy or generated-family ideas are production-safe.

The service, artifact, packaging, JVM tuple, workload, and runtime policy are
part of the claim identity. Additional services require their own prospective
campaigns.

## 9. Practical adoption

An engineer evaluating JMOA should begin in report-only mode:

1. build the service without optimization;
2. capture a representative profile from matching bytecode;
3. inspect site coverage, exclusions, and proposed transformations;
4. choose full transformation or reducer-only scope explicitly;
5. materialize the real deployment shape;
6. verify artifact replacement and runtime origins;
7. run semantic smoke tests;
8. qualify same-artifact memory variance;
9. execute paired confirmation with CPU, startup, and latency budgets.

The correct outcome may be rejection. JMOA is useful when it shows that a
candidate is unsafe, inactive, too noisy to measure, or not worth its cost.

JMOA 2.1 is distributed through GitHub Releases. The Maven plugin compiles for
Java 22 tooling, while the runtime library targets Java 17. The measured
PetClinic process used Java 17. Public source, release assets, checksums, and
sanitized evidence are available in this repository.

## 10. Engineering contributions

JMOA 2.1 contributes an integrated approach rather than a new isolated JVM
primitive:

1. **Profile-linked bytecode admission.** Transformation decisions retain a
   stable connection to observed execution.
2. **Build-time production shape.** The final service requires no optimization
   javaagent.
3. **Classfile component auditing.** Target metadata changes are allowed while
   unexplained byte differences are rejected.
4. **Deployment-aware verification.** Fat-JAR and exploded-Boot artifacts are
   measured as explicit contracts with origin proof.
5. **Multi-layer memory attribution.** PSS, dirty pages, cgroup accounting, NMT,
   heap, and native mappings are interpreted together.
6. **Prospective claim discipline.** Control gates, estimators, costs, and
   terminal states are frozen before held-out exposure.
7. **Atomic evidence publication.** The accepted artifact pointer and
   experiment registry advance together or remain unchanged.

For a hiring or technical-review audience, the project demonstrates work
across bytecode engineering, Maven plugin development, Spring Boot packaging,
Linux/cgroup measurement, statistical experiment design, failure analysis,
release engineering, and technical communication.

## 11. Future work

PetClinic is the first service published under the 2.1 direct-product wording.
Future service examples should preserve the same rules:

- independent same-artifact variance qualification;
- exact artifact and runtime-policy identity;
- service-specific cost budgets;
- held-out paired or blocked inference;
- disclosure of packaging and content interactions;
- no transfer of PetClinic effect size to a new service.

Technical development can expand supported SAM shapes and improve packaging
selection, but new transformation families should remain report-only until
their safety and economics are independently demonstrated.

## 12. Conclusion

JMOA's core idea is simple: an optimization is not complete when bytecode
changes; it is complete when the exact deployed service is semantically valid,
proven to contain the intended artifacts, and shown to use less memory under a
qualified protocol.

The PetClinic 2.1 campaign met that standard. Exact R41F delivered a
packaging-inclusive 14.88 MiB median process-PSS reduction and 16.24 MiB
target-cgroup RAM reduction across 12/12 held-out blocks. The result includes a
14.71% lifecycle-CPU tradeoff and does not pretend that content alone explains
the full delta.

That combination—optimization, attribution, refusal, and precise public
wording—is the product JMOA is building.

## Reproducibility and citation

- [Human-readable PetClinic result](../product-evidence/petclinic-r41f-b0-t7r23-result.md)
- [Machine-readable PetClinic result](../product-evidence/petclinic-r41f-b0-t7r23-result.json)
- [System architecture](../architecture/system-overview.md)
- [Measurement protocol](../methodology/measurement-protocol.md)
- [Statistics and acceptance](../methodology/statistics-and-acceptance.md)
- [Memory attribution](../methodology/memory-attribution.md)
- [Release notes](../releases/v2.1.0.md)

Suggested citation:

```text
JMOA Project. “JMOA 2.1: Evidence-Gated Build-Time JVM Footprint
Optimization.” Technical report, AlphaSudo, 2026.
https://github.com/AlphaSudo/jmoa/releases/tag/v2.1.0
```
