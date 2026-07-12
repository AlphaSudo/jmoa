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
This first screen did not allow a claim. It was later followed by one clean
diagnostic rerun and then a fresh 3-pair confirmation, which failed. This file
is retained as the first-screen evidence, not as the final V2-Q verdict.
