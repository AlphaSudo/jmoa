# PetClinic R41F-to-V2E R4.87 RAM Confirmation

## Verdict

R4.87 is a **claimable PetClinic RAM win with bounded lifecycle-CPU and file
tradeoffs**. Exact R41F reduced median process PSS by 12,797 KiB (about 12.5
MiB) versus accepted exact V2E. All 12 held-out paired blocks favored R41F, the
exact two-sided sign-test result was `p=0.00048828125`, and the seeded paired
median bootstrap 95% interval was [-13,299.5, -12,005] KiB.

The claim is candidate-specific and protocol-specific. It is not a universal
JMOA claim, a clean no-JMOA comparison, or a production-promotion decision.

## What Was Compared

| Arm | Deployment | Artifact identity |
| --- | --- | --- |
| A | Accepted V2E, exploded Spring Boot | Tree SHA-256 `A8A1A1565E697411CCB639FC47B4A92C95A900CF5D66845039E04DB1885B53A2` |
| D | R41F, deterministic Spring Boot fat JAR | File SHA-256 `415217CE9926ECA2BC0052800DD456291D438A9C63589CBFE5A55238BE71EB9F` |

R41F was derived from exact V2E by replacing 104 of 161 runtime dependency
JARs with independently audited reductions and then materializing the same
logical content as a deterministic fat JAR. Across 21,017 changed class files,
the admitted structural change was removal of `LocalVariableTable` and
`LocalVariableTypeTable` attributes. R41F and its exploded R41E counterpart
have an equal 56,295-record logical tree.

The comparison therefore measures the complete R41F deployment, including
both metadata compaction and packaging. It cannot assign the full observed RAM
delta to metadata removal alone.

## Prospective Protocol

The R4.87 contract was frozen at `2026-09-12T08:28:31Z`, before the fresh
observations. It reused no R4.85 or R4.86 observation in inference and permitted
no post-observation gate change.

The campaign collected:

- eight fresh same-artifact control sessions: two A/A pairs and two D/D pairs;
- 24 fresh held-out sessions in 12 paired, counterbalanced A/D blocks;
- three sealed claim frames per session, reduced to the session estimator; and
- semantic, process-memory, cgroup, mapping, heap, NMT, class, startup, latency,
  CPU, host-pressure, coldness, swap, and teardown evidence.

Both arms used the same no-CDS low-dirty runtime policy on Temurin 17.0.19+10
with Serial GC, `-Xms32m`, `-Xmx256m`, `-Xss256k`, a 48 MiB reserved code
cache, two compiler threads, NMT summary mode, a five-second native-heap trim
interval, and `MALLOC_ARENA_MAX=1`. Every session proved swap was disabled and
zero, no OOM event occurred, memory pressure stayed inside the frozen envelope,
and the reset completed before the target container was created.

## Primary Result

All deltas are R41F minus V2E; negative memory values favor R41F.

| Metric | Median delta | Supporting result |
| --- | ---: | --- |
| Process PSS | **-12,797 KiB** | 12/12 favorable; 95% CI [-13,299.5, -12,005] KiB |
| Baseline-debited process PSS | **-12,797 KiB** | 12/12 favorable; 95% CI [-13,127.5, -11,867] KiB |
| Private Dirty | **-12,650 KiB** | Favorable corroboration |
| `memory.current` | **-13,932,544 bytes** | 95% CI [-14,340,096, -13,438,976] bytes |
| cgroup anonymous memory | **-12,959,744 bytes** | Dominant cgroup reduction |
| Java heap mapping PSS | **-13,672 KiB** | Favorable |
| Anonymous read/write PSS | **-17,064 KiB** | Favorable |
| NMT total committed | **-27,596 KiB** | Favorable |
| Metaspace used | **-2,120 KiB** | Favorable |
| Loaded classes | **-19** | Favorable |

The baseline-debited estimator uses only adverse A-control residuals to weaken
the candidate result; D residuals receive no favorable credit. Its unchanged
median and fully negative interval show that the RAM conclusion does not depend
on ignoring measured same-artifact baseline movement.

## Disclosed Tradeoffs

| Metric | Observed delta | Frozen limit | Result |
| --- | ---: | ---: | --- |
| Lifecycle CPU, median relative | +19.2017% | <= +20% | Passed |
| Lifecycle CPU, median absolute | +5,883,954.5 microseconds | <= +6,500,000 microseconds | Passed |
| Lifecycle CPU, bootstrap upper 95% | +20.0757% | <= +25% | Passed |
| cgroup file, median | +106,496 bytes | <= +1,048,576 bytes | Passed |
| cgroup file, worst block | +278,528 bytes | <= +1,048,576 bytes | Passed |
| cgroup file, one-sided upper 95% | +135,168 bytes | <= +1,048,576 bytes | Passed |
| Native process `[heap]` PSS | +3,080 KiB | <= +4,096 KiB | Passed |
| Startup | +2,586.5 ms / +6.4861% | <= +10% | Passed |
| Request latency median | 0 ms | <= +10% | Passed |
| Request latency p95 | +1 ms / +2.8595% | <= +11.9048% | Passed |

The native process-heap increase is real, but it is outweighed by a -13,672
KiB Java-heap mapping delta: combined Java/native heap PSS is -10,592 KiB.
Anonymous read/write PSS and NMT committed memory independently satisfy the
prospectively frozen compensation rules.

## Integrity And Claim Boundary

All frozen primary-memory, baseline-debit, `memory.current`, file, native
component, compensation, semantic, startup, latency, CPU, residual-reclaim,
cold-reset, host-pressure, swap, and teardown gates passed. The experiment
registry moved atomically from 16 to 17 rows and contains exactly one R4.87 row.

The authoritative full raw campaign remains outside Git because it contains
machine-local paths and full runtime captures. The public machine-readable
result preserves the complete claim statistics, frozen limits, artifact
identities, per-block PSS deltas, and SHA-256 bindings to the sealed raw
contract, analysis, terminal record, and registry.

Claimability does not promote R41F. Accepted PetClinic remains exact V2E until
a separately frozen T7R campaign evaluates the promotion question. The earlier
clean B0-to-V2 result also remains unchanged: R4.87 is a V2E-to-R41F engineering
comparison and must not be substituted into or arithmetically added to the
three-service buyer matrix. Doctor and Patient are outside this result.

See the [machine-readable result](petclinic-r41f-v2e-r487-result.json) and the
[direct-result reconciliation](direct-result-reconciliation.md).
