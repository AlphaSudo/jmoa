# Patient Final V2 Policy Verdict

Status: `BASE_CDS_AND_NO_CDS_CONFIRMED_APP_CDS_BLOCKED`

Patient now has two confirmed service-specific runtime policies. The corrected
artifact passes the frozen V1-to-V2 gate under both `JDK_BASE_CDS_LOW_DIRTY`
and `NO_CDS_LOW_DIRTY`. Dynamic Patient application archives remain blocked.

## Policy Decision

| Policy | Decision | Evidence |
| --- | --- | --- |
| `JDK_BASE_CDS_LOW_DIRTY` | `RECOMMEND_CONFIRMED_POLICY` | 6/6 valid, 3/3 wins, median PSS -8,279 KB |
| `NO_CDS_LOW_DIRTY` | `RECOMMEND_CONFIRMED_POLICY` | 6/6 valid, 2/3 wins, median PSS -8,903 KB |
| Patient application CDS | `BLOCK_RUNTIME_PROMOTION` | Dynamic and extracted-layout application archives failed their frozen admission gates |

## Frozen Base-CDS Gate

| Gate | Result |
| --- | --- |
| Valid runs | 6/6 |
| Paired wins | 3/3 |
| Median PSS | -8,279 KB <= -4,096 KB |
| Median Private_Dirty | -8,444 KB <= -1,024 KB |
| Median memory.current | -8,523,776 B <= -1,048,576 B |
| Workload errors | 0 |
| V2-C | `CONFIRMED_WIN` |
| V2-D | `HEAP_PAGE_TOUCH_REDUCTION` |

## Frozen No-CDS Gate

| Gate | Result |
| --- | --- |
| Valid runs | 6/6 |
| Paired wins | 2/3 |
| Median PSS | -8,903 KB <= -4,096 KB |
| Median Private_Dirty | -8,636 KB <= -1,024 KB |
| Median memory.current | -9,707,520 B <= -1,048,576 B |
| Workload errors | 0 |
| V2-C | `CONFIRMED_WIN` |
| V2-D | passed |

The aggregate three-service matrix is now eligible under service-specific
confirmed policies: PetClinic no-CDS, Doctor application CDS, and Patient stock
JDK base CDS. Patient no-CDS is also independently confirmed. These results do
not transfer to another service, archive, or launch shape.

No further Patient V2 performance experiment is authorized by this record.

The later [Patient Dynamic AppCDS Archive Economics Study](../runtime-policy-studies/patient-dynamic-appcds-study.md)
was explicitly registered after `v2.0.0` as a non-blocking terminal policy
exception study. It did not reopen or alter this V2 launch gate.

The final [extracted-layout common-class AppCDS study](../runtime-policy-studies/patient-extracted-common-appcds-study.md)
also failed its fixed-artifact admission gate. It is the permanent stop for
single-replica Patient AppCDS work; the V1-to-V2 APP screen was therefore not
run. This blocks Patient application CDS; it does not invalidate the later
stock-base-CDS or no-CDS confirmations.
