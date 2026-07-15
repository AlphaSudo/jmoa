# Patient Final V1 -> V2 Verdict

Status: **FAIL_PATIENT_NOT_CONFIRMED**

The Patient artifact freeze and semantic pre-gates passed, but the final V1 -> V2 runtime result does not satisfy the frozen three-service acceptance contract.

## Artifact freeze

| Item | Final V1 | Final V2 | Delta |
|---|---:|---:|---:|
| Outer application JAR bytes | 99,585,809 | 95,770,035 | -3,815,774 |
| Embedded dependency bytes | 98,600,337 | 94,847,956 | -3,752,381 |
| Embedded dependency JARs | 162 | 162 | 0 |
| Reduced classes | n/a | 32,624 | n/a |

Artifact identities are recorded in the accompanying JSON verdict. The materialization and semantic pre-gates passed, with all 162 embedded dependency entries replaced and no workload or linkage errors in the pre-gate smoke.

## Runtime confirmation

Protocol: Spring Boot fat JAR, CDS enabled with variant-specific archives, Java 26, no runtime javaagent, and 600 health/Prometheus requests per run.

| Metric | Result | Required |
|---|---:|---:|
| Valid runs | 6/6 | 6/6 |
| Paired wins | 1/3 | >= 2/3 |
| Median PSS delta | +1,652 KB | <= -4,096 KB |
| Median Private_Dirty delta | +1,756 KB | <= -1,024 KB |
| Median memory.current delta | -774,144 B | <= -1,048,576 B |
| Workload errors | 0 | 0 |
| V2-C | MIXED_METRICS_NEEDS_RERUN | CONFIRMED_WIN |
| V2-D | present | required |

V2-D ranked `ANONYMOUS_RW_ALLOCATOR_GROWTH` as the primary hypothesis. The result is therefore a valid, analyzed non-win, not missing evidence.

## Bounded diagnostic

One additional screen used the corrected protocol: page-cache reset before each variant, a 20-second post-health settle, and the same CDS policy. It also regressed:

| Metric | Diagnostic delta |
|---|---:|
| PSS | +7,579 KB |
| Private_Dirty | +7,656 KB |
| memory.current | +4,935,680 B |
| anonymous_rw PSS | +10,504 KB |

This bounded check was screen evidence only and was not promoted to confirmation.

## Decision

Patient is **not confirmed**. The aggregate three-service matrix remains blocked. The PetClinic and Doctor wins remain valid within their own documented scopes, but they cannot be promoted to a stable three-service headline until Patient passes the frozen contract.

Raw Patient run folders, private configuration, database details, CDS archives, and local paths remain outside the public repository.
