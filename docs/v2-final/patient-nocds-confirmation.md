# Patient No-CDS Confirmation

Status: `CONFIRMED_WIN`

This is the final bounded Patient V1-to-V2 confirmation under
`NO_CDS_LOW_DIRTY`. It uses the corrected V2 artifact whose reducer scope
excludes `jmoa-runtime-lib`.

The reusable capture entry point is
[`scripts/run-patient-nocds-confirmation.ps1`](../../scripts/run-patient-nocds-confirmation.ps1).
The machine-readable record is
[`patient-nocds-confirmation.json`](patient-nocds-confirmation.json).

## Frozen Protocol

- Service: private `patient-service`
- Launch mode: Spring Boot fat JAR
- Runtime policy: `NO_CDS_LOW_DIRTY`
- CDS, AppCDS, and Leyden: disabled
- Runtime javaagent: absent
- `MALLOC_ARENA_MAX=1`
- Java 26, Serial GC, identical heap/thread/code-cache settings
- Page-cache reset before every variant
- 20-second readiness warmup and T+20 post-workload capture
- 600 requests per run, zero workload errors
- Pair order: V1 -> V2, V2 -> V1, V1 -> V2

The live JVM proof in all six manifests recorded `-Xshare:off`, no agent/CDS
options, and `MALLOC_ARENA_MAX=1`. Class-load logging was intentionally absent
from the clean memory pairs to avoid diagnostic perturbation; the record makes
no dynamic class-origin claim from those pairs.

## Artifact and Semantic Gates

| Gate | Result |
| --- | --- |
| Corrected V1 artifact | `17FDD9...E188` |
| Corrected V2 artifact | `4CFC40...69E3` |
| Embedded dependency entries | 162 -> 162 |
| Runtime support JAR | byte-identical |
| Raw audited classes | 32,616; 0 preservation failures |
| Dependency JAR byte delta | -3,752,212 B |
| Outer application JAR byte delta | -3,815,605 B |
| Semantic pre-gate | passed |
| Materialization proof | static passed; dynamic origin not captured in clean pairs |
| Six-run workload | 3,600 requests; 0 errors |
| Semantic/linkage errors | 0 |
| Live no-CDS proof | 6/6 passed |

## Pair Results

| Pair | Order | PSS delta | Private_Dirty delta | memory.current delta | Gate |
| ---: | --- | ---: | ---: | ---: | --- |
| 1 | V1 -> V2 | +2,971 KB | +2,684 KB | +3,256,320 B | fail |
| 2 | V2 -> V1 | -8,903 KB | -8,636 KB | -9,707,520 B | pass |
| 3 | V1 -> V2 | -9,650 KB | -9,572 KB | -9,932,800 B | pass |

## Frozen Gate Result

| Metric | Result | Requirement |
| --- | ---: | ---: |
| Valid runs | 6/6 | 6/6 |
| Paired wins | 2/3 | >= 2/3 |
| Median PSS | -8,903 KB | <= -4,096 KB |
| Median Private_Dirty | -8,636 KB | <= -1,024 KB |
| Median memory.current | -9,707,520 B | <= -1,048,576 B |
| Workload and semantic errors | 0 | 0 |
| V2-C | `CONFIRMED_WIN` | `CONFIRMED_WIN` |
| V2-D | passed | required |

The result clears the substantial 4 MiB PSS threshold despite one regressing
pair. The result is confirmed only for this corrected artifact, launch shape,
no-CDS policy, allocator setting, and workload protocol.

The prior CDS failure remains authoritative for the CDS policy. The combined
policy decision is `NO_CDS_CONFIRMED_CDS_BLOCKED`.
