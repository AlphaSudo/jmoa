# PetClinic JMOA 2.1 Direct RAM Result

## Verdict

**Claimable direct whole-deployment RAM win.**

The exact accepted R41F JMOA fat-JAR deployment reduced Spring PetClinic
customers-service process PSS by **15,241.5 KiB (14.88 MiB)** and target-cgroup
`memory.current` by **17,033,216 bytes (16.24 MiB)** relative to the documented
strict no-JMOA B0 exploded-Boot deployment.

The terminal decision is `T7R23_R487_SCALE_DIRECT_RAM_WIN`. The claim basis is
`DIRECT_PRODUCT`, and the result tier is `R487_SCALE`.

## Exact claim boundary

| Role | Frozen arm | Content | Launch shape |
| --- | --- | --- | --- |
| Comparator | `B0E` | Strict no-JMOA B0 | Exploded Spring Boot |
| Treatment | `R41F` | Exact accepted R41F JMOA artifact | Spring Boot fat JAR |

The measured contrast is `R41F - B0E`. It includes both optimized content and
deployment packaging. It is not a content-only estimate, not “all JMOA versus
all Spring Boot,” and not a universal statement about fat JARs or exploded
Boot. The result applies to the artifact identities, runtime tuple, workload,
and protocol disclosed below.

## Primary held-out result

| Metric | Median delta | Favorable blocks | Exact sign p | Bootstrap 95% interval |
| --- | ---: | ---: | ---: | ---: |
| Process PSS | **-15,241.5 KiB** | 12/12 | 0.00048828125 | [-16,109.5, -15,052.5] KiB |
| Private Dirty | **-15,252 KiB** | 12/12 | 0.00048828125 | [-16,062, -14,880] KiB |
| `memory.current` | **-17,033,216 B** | 12/12 | 0.00048828125 | [-17,842,176, -16,713,728] B |
| Cgroup anonymous | **-15,616,000 B** | 12/12 | 0.00048828125 | [-16,445,440, -15,243,264] B |
| Cgroup file | **-411,648 B** | 12/12 | 0.00048828125 | [-450,560, -362,496] B |
| Cgroup kernel | **-1,079,296 B** | 12/12 | 0.00048828125 | [-1,097,728, -1,067,008] B |
| `memory.peak` | **-19,451,904 B** | 10/12 | 0.03857421875 | [-27,238,400, -11,542,528] B |

The conservative baseline-residual debit did not change the primary median.
Debited PSS remained **-15,241.5 KiB**, favorable in 12/12 blocks, with the
same wholly negative bootstrap interval.

## Product costs

| Metric | Median R41F - B0E | Relative median | Verdict |
| --- | ---: | ---: | --- |
| Lifecycle CPU | +4,841,192 µs | **+14.7073%** | Frozen cost gates passed |
| Startup | +1,652 ms | **+3.7611%** | Frozen cost gate passed |
| Median request latency | 0 ms | 0% | Passed |
| p95 request latency | 0 ms | 0% | Passed |
| Loaded classes | -175 | -0.7144% | Favorable |

The release claim therefore means **less measured RAM with a bounded CPU and
startup tradeoff**. It does not mean the optimization is free.

## Attribution

| Component | Median delta |
| --- | ---: |
| Java-heap PSS | **-15,092 KiB** |
| Native process `[heap]` PSS | **+2,972 KiB** |
| Anonymous PSS | **-15,250 KiB** |
| File PSS | -79 KiB |
| Shared-memory PSS | 0 KiB |
| RSS | -15,384 KiB |

The native process heap moved adversely even though process PSS and every
target-cgroup component moved favorably. Reporting that component prevents an
incorrect claim that every memory subsystem improved.

## Four-arm factorial

The campaign also ran strict baseline and R41F content in both exploded and
fat-JAR layouts. These secondary estimates explain the deployment result; they
do not replace the primary direct contrast.

| Secondary contrast | Median PSS delta | 95% bootstrap interval |
| --- | ---: | ---: |
| Baseline packaging, `B0F - B0E` | -14,725.5 KiB | [-15,356.5, -14,089] KiB |
| JMOA content within fat layout, `R41F - B0F` | -979 KiB | [-1,187, -226.5] KiB |
| JMOA content within exploded layout, `R41E - B0E` | -4,493.5 KiB | [-6,169, -3,195] KiB |
| Optimized packaging, `R41F - R41E` | -10,875 KiB | [-12,193.5, -9,362.5] KiB |
| Content main effect | -2,465 KiB | [-3,574, -2,151] KiB |
| Packaging main effect | **-12,715.25 KiB** | [-13,113, -12,077] KiB |
| Content × packaging interaction | +3,801.5 KiB | [2,389.5, 5,190] KiB |

Packaging is the dominant measured main effect in this deployment. The positive
interaction means the two effects are not simply additive. The fat-layout
content contrast was favorable in 9/12 blocks but its exact sign-test p-value
was 0.14599609375; it is descriptive, not a standalone claim.

## Execution shape

| Stage | Units | Sessions |
| --- | ---: | ---: |
| Qualification | 5 | 5 |
| Same-artifact controls | 10 pairs | 20 |
| Direct screen | 4 pairs | 8 |
| Held-out four-arm factorial | 12 blocks | 48 |
| **Total** | 31 | **81** |

All 81 attempted sessions completed. There were no replacement observations
and no predecessor observations were reused. The campaign retained a maximum
ceiling of 91 sessions but required no replacement capacity.

## Runtime tuple

- Eclipse Temurin 17.0.19+10;
- Spring Boot 4.0.1;
- Serial GC;
- `-Xms32m`, `-Xmx256m`, `-Xss256k`;
- 48 MiB reserved code cache and two compiler threads;
- Native Memory Tracking `summary`;
- CDS disabled;
- native-heap trim interval 5,000 ms;
- `MALLOC_ARENA_MAX=1`;
- 512 MiB target-cgroup memory limit;
- 20-second warmup;
- 81-request semantic workload at 200 ms pacing;
- stopped claim frames at +10, +15, and +20 seconds;
- no JMOA javaagent in the measured process.

## Artifact identity

| Artifact | SHA-256 / image identity |
| --- | --- |
| Strict B0 content | `2F4A63A803B2A05050ACF020AD382ABE589C52EE50253A1F5DC85F6D62584ACF` |
| Exact R41F artifact | `415217CE9926ECA2BC0052800DD456291D438A9C63589CBFE5A55238BE71EB9F` |
| Accepted R41F image | `7CC8F1766B9AC45983DFD634846060A6C62209259CCB40596972A3168A5CABEB` |
| R41 logical content | `A84A1D7EB79C0F476999B74081F2ED3457910D2B15916D5E27576EB486F64C25` |

The accepted deployment remains exact R41F. JMOA 2.1 advances its evidence
pointer to revision 3; it does not substitute a new binary after measurement.

## Evidence seals

| Evidence object | Bytes | SHA-256 |
| --- | ---: | --- |
| Prospective contract | 281,736 | `56C92307339B26B7B02CD22AA7CDDF223819375F69545509DC7BC68752E95C15` |
| Fresh numerical analysis | 119,111 | `C747F82302D4FFBE4D62B44BBC1F6BFBF686E0D9F91A42E5EAAE7AE429ABDCF8` |
| Attribution disclosure | 601,538 | `6D3F916D2049BE2FEA6358D7B1CCD568F006FDA3D5D3D4807E8E7C78EFC98267` |
| Public result | 109,684 | `EDF3273DD3358CDA6ED0AEDF20BED70418BED27EC2D5CF1D3B3936706099210B` |
| Progress journal | 46,899 | `D7D289E78FA378993C8C74F7BF124B86796CC5F243CCDBBABA0FCFA21A7FE1BC` |
| Terminal closeout | 3,394 | `635F83282A700870C5A35010B58F67B3E6BF77D8E05E2D027A430FAFFA4CE340` |
| Publication receipt | 2,300 | `67D3EA6253F43BFB280B9B22878CA15E87278005013FBE27C392C914D99FF941` |

The public repository contains the sanitized result and hashes rather than
machine-local raw paths or private run storage.

## Defensible public wording

> Under the frozen agent-free, CDS-off PetClinic protocol, the exact accepted
> R41F JMOA fat-JAR deployment reduced process PSS by 15,241.5 KiB and
> target-cgroup `memory.current` by 17,033,216 bytes versus the documented
> strict no-JMOA B0 exploded-Boot deployment. All 12 held-out blocks favored
> R41F; median lifecycle CPU increased 14.71%. This is a packaging-inclusive,
> service-specific result.

Do not shorten that to “JMOA always saves 15 MiB,” “metadata removal saves
15 MiB,” or “fat JARs always use less RAM.”
