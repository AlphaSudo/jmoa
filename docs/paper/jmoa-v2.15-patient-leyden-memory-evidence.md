# JMOA 2.15: Patient memory evidence beyond the 2.1 PetClinic study

Published 2026-10-13. Companion to the [JMOA 2.1 PetClinic paper](jmoa-v2.1-petclinic-memory-engineering.md).

## Abstract

JMOA 2.15 reports a direct comparison of a clean Patient baseline and a composed
JMOA candidate using Java 26 and artifact-specific Leyden AOT caches. Twelve
fresh paired blocks measured **8.38 MiB lower median stopped-process PSS** after
the fixed workload, with a **conditional 8.19 MiB lower saving bound**. The
original calibration failed and was admitted through an approved post-observation
exception. Performance bounds remain unqualified or failed. Startup/cache warmth,
production parity and uploaded-binary parity remain unqualified. This release
adds benchmark evidence, with no new Maven binaries.

## 1. What changed between the publications

| Dimension | Stable 2.1 PetClinic | Provisional 2.15 Patient |
| --- | --- | --- |
| Question | Exact accepted whole deployment versus strict no-JMOA baseline | Direct clean B0 versus composed candidate with Leyden retained |
| Runtime | Java 17; no CDS | Temurin 26.0.2.1+1; own Leyden cache for each artifact |
| Packaging | R41F fat JAR versus B0 exploded Boot | Frozen Patient application artifacts and separate cache identities |
| Main endpoint | Stopped-process PSS and target-cgroup memory | Stopped Patient-process PSS after a fixed 632-request primary workload |
| Held-out data | 12 four-arm blocks, 48 observations | 12 two-arm blocks, 24 observations |
| Process-PSS saving | 14.88 MiB | 8.38 MiB |
| Inference | Frozen sign test and bootstrap intervals; accepted gates | Conditional order-statistic bound; failed original calibration admitted by exception |
| Product costs | Frozen CPU/startup/latency gates passed | Cost calibration and stationarity unqualified; five confirmation metrics failed |
| Distribution | Plugin/runtime JARs, sources, POMs and checksums | Evidence publication only; stable coordinates remain 2.1.0 |

The table is not a version-speedup comparison. Different workloads, JDKs,
deployment contracts and acceptance states prevent a causal interpretation of
the difference between the two RAM numbers.

## 2. Common engineering method

Both studies connect representative profiling, bounded lambda/adapter rewriting,
artifact audits, deployment materialization and runtime identity to paired Linux
memory evidence. Final service processes use no JMOA optimization javaagent.
Training and diagnostic instrumentation are separate from the claim endpoint.

Patient's audited static transformation coverage was **788 rewritten sites**:
455 Tier 1 and 333 Tier 2, across 498 owners, with 192 logical and 195 physical
helpers. These are static call sites in the admitted artifact; they are not
runtime invocation counts or proof that every site executed in every request.

The common Patient runtime policy includes Serial GC, compact headers, Xms32m,
Xmx256m, a 256 KiB thread stack, 48 MiB code cache and NMT summary. Both arms use
`MALLOC_ARENA_MAX=1` and a 5,000 ms native-heap trimming interval. Those common
settings do not identify a treatment-only saving. Each arm's application and
AOT cache has its own recorded SHA-256; a baseline cache is not reused for
transformed bytes.

The stable public raw reducer remains opt-in LVT/LVTT reduction. Patient's
composed experimental treatment also reduced dependency line-number
observability. That experimental scope does not broaden the shipped 2.1 reducer
policy, which preserves line numbers and other non-target structures.

## 3. Direct measurement, not added historical medians

The result comes from twelve adjacent B0/candidate pairs with balanced AB/BA
order. Median saving was **8,586 KiB (8.384765625 MiB)**. Eleven of twelve pair
savings strictly exceeded 8,192 KiB; the remaining pair saved 8,096 KiB.
Historical 5.71 MiB and incremental 2.41 MiB references were not added.

The third-smallest observed saving, **8,384 KiB (8.1875 MiB)**, is the declared
lower bound. Its nominal one-sided coverage is 98.0713% under independent,
exchangeable, stable pairs. These assumptions are explicit and unproved.
The conditional one-sided sign result for exceeding 8 MiB is p=0.003173828125.
Observed order and time diagnostics were within the frozen 1,024 KiB drift
bounds; this does not establish the model assumptions.

PSS apportions shared mapped pages to the process. Cgroup file charges and
cache-file bytes are separate accounting quantities; they are not added to PSS.
A smaller application or cache file alone does not prove smaller resident RAM.

## 4. Calibration exception and sensitivity

The eight fresh B0 controls failed the original precision gates: maximum absolute
difference was **540 KiB** against a **512 KiB** limit; absolute median bias was
**176 KiB** against a **128 KiB** limit. The exact observed control block was
approved after observation and before fresh confirmation. The original
calibration remains failed.

Subtracting the observed 176 KiB bias magnitude from the lower bound yields
8,208 KiB. Subtracting the maximum observed control difference yields 7,844 KiB.
These are diagnostic sensitivity illustrations, not established bias corrections
or confidence bounds. They cannot turn the exception into a passed calibration.

## 5. Performance and observability costs

The public benchmark discloses all **82 cost metrics**. Five confirmation margin
tests failed: startup, early read-user, early actuator-prometheus, early
stroke-low and repeated delete. Null cost calibration also failed; stationarity
and assessability did not qualify. A numerically passing row is not an accepted
performance bound when the surrounding calibration assumptions are unqualified.

Dependency line-number observability was reduced. The workload used a finite
synthetic stroke fixture, with no live ML equivalence; ConfigClient was absent.
These costs and coverage limits belong with the RAM number.

## 6. Lineage and reproducibility

The benchmark record publishes exact application/cache hashes, shared-core hash,
JRE file identities, pair observations, cost results and archival report hashes.
It accounts for 96 runtime attempts including failures; incomplete earlier
confirmation epochs were excluded in full. A saved raw replay checked 76
successful units. That replay is an internal consistency check, not independent
external replication.

The tested producer was an admitted compatibility port of the 2.1 approach.
Byte identity with the uploaded stable 2.1 plugin/runtime artifacts is unresolved.
Consequently 2.15 contains no new Maven binary distribution and does not promise
that installing stable 2.1.0 reproduces the Patient effect.

Private service source, raw requests, host logs and raw captures are not public
reproduction inputs. Public aggregates allow result inspection and arithmetic
checks, not independent rebuilding of the private Patient experiment.

## 7. Interpretation

The observed scoped RAM reduction is supported by the declared paired record.
It is provisional evidence with a failed original calibration and unqualified or
failed performance bounds. Cold-start capacity, startup/primary-workload warmth,
whole-stack RAM, production parity and released-binary parity are not established.

Stable 2.1 retains its accepted packaging-inclusive PetClinic result and measured
CPU/startup tradeoffs. Provisional 2.15 broadens the documented service and cache
context; it does not replace that accepted result or establish universal savings.

## Records

- [Patient benchmark, pairs and all cost results](../product-evidence/patient-scoped-ram-benchmark.md)
- [Machine-readable Patient record](../product-evidence/patient-scoped-ram-benchmark.json)
- [PetClinic accepted result](../product-evidence/petclinic-r41f-b0-t7r23-result.md)
- [JMOA 2.15 release](https://github.com/AlphaSudo/jmoa/releases/tag/v2.15)
