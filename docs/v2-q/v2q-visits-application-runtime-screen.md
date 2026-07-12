# V2-Q Visits Application Runtime Screen

Status: `FAILED_PROMOTION_GATE`.

This clean single screen compared public visits dependency raw only against the
same dependency raw artifact plus the V2-Q admitted application-class reducer.
Both variants completed the 18-request workload with zero linkage errors.

| Metric | Delta, candidate minus baseline |
| --- | ---: |
| PSS | `+1,432 KB` |
| Private_Dirty | `+1,892 KB` |
| memory.current | `+1,851,392 bytes` |

All three memory gates were worse than the allowed `+1 MB` screen tolerance.
V2-Q therefore stops before confirmation, V2-C validation, and V2-D
attribution. This is not a runtime performance claim.
