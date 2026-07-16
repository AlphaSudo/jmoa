# Patient Final V2 Policy Verdict

Status: `NO_CDS_CONFIRMED_CDS_BLOCKED`

Patient now has a confirmed service-specific runtime policy. The corrected
artifact passes the frozen V1-to-V2 gate under `NO_CDS_LOW_DIRTY`; the separate
corrected CDS confirmation remains blocked.

## Policy Decision

| Policy | Decision | Evidence |
| --- | --- | --- |
| `CDS` | `BLOCK_RUNTIME_PROMOTION` | 6/6 valid, 1/3 wins, median PSS +668 KB |
| `NO_CDS_LOW_DIRTY` | `RECOMMEND_CONFIRMED_POLICY` | 6/6 valid, 2/3 wins, median PSS -8,903 KB |

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
confirmed policies: PetClinic no-CDS, Doctor CDS, and Patient no-CDS. This does
not mean CDS is universally good or that the Patient no-CDS result transfers to
another launch shape.

No further Patient V2 performance experiment is authorized by this record:
do not retune CDS, rebuild JSA archives, change thresholds, or introduce a new
reducer to chase a broader claim.
