# PetClinic Independent-Session V1 Result

## Verdict

```text
PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST
```

The final preregistered protocol ran on Debian 13 with 4 vCPUs, 1,953,536 KB
RAM, and swap disabled. It used one fresh support stack, one target JVM, one
81-request workload, one claim capture, and complete teardown per observation.

All three B0 sessions were valid:

| Session | PSS | Private_Dirty | `memory.current` | Requests | Errors |
| --- | ---: | ---: | ---: | ---: | ---: |
| B0-Q1 | 350,394 KB | 341,820 KB | 454,860,800 B | 81 | 0 |
| B0-Q2 | 347,356 KB | 338,908 KB | 451,538,944 B | 81 | 0 |
| B0-Q3 | 348,918 KB | 340,484 KB | 453,414,912 B | 81 | 0 |

The frozen qualification gate failed:

| Metric | Observed range | Limit | Result |
| --- | ---: | ---: | --- |
| PSS | 3,038 KB | 1,024 KB | Fail |
| Private_Dirty | 2,912 KB | 1,024 KB | Fail |
| `memory.current` | 3,321,856 B | 2,097,152 B | Fail |

V2 qualification, the six-session product block, V2-C, and V2-D were not run.
This is the required permanent stop condition for this VM.

## Excluded Diagnostic

The preregistered B0/B0/B0 shared-support diagnostic was excluded from product
evidence. Its PSS values were `350,316`, `349,683`, and `354,163` KB, classified
`RANDOM_OR_MIXED`. Each target completed 81 requests with zero errors.

The older controls were separately classified
`SECOND_POSITION_NATIVE_ANON`; see the
[period-effect attribution](petclinic-b0-period-effect-attribution.md).

## Audit Trail

Four scenario ledgers preserve every external command and HTTP response:

```text
diagnostic B0/B0/B0: 512 records
B0-Q1: 189 records
B0-Q2: 186 records
B0-Q3: 186 records
total: 1,073 records
```

The raw archive is intentionally outside Git:

```text
bytes: 5,868,737
SHA-256: F984B70649EF6A6527A98F79F1923FD09E6E67273FE7A380A71EFA4BDD54089A
```

Two earlier orchestration attempts are retained outside Git. One exposed a
read-only PowerShell automatic-variable collision; the next exposed Linux bare
boolean serialization. Both were fixed in source and guarded by Debian-native
fixtures before this authoritative run.

## Claim Boundary

There is no B0-to-V2 result from this protocol. The outcome is not a JMOA
regression, V2 failure, capacity failure, semantic failure, or artifact
corruption. The current Hyper-V VM cannot resolve the frozen 4 MiB direct
product effect. Any future direct PetClinic campaign must move to bare-metal or
dedicated Linux; no further protocol redesign is authorized on this VM.
