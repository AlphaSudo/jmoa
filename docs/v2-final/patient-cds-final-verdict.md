# Patient CDS Final Verdict

Status: `BLOCK_RUNTIME_PROMOTION`

This is the authoritative Patient **CDS** record. It is preserved separately
from the later no-CDS policy confirmation and is not rewritten by that result.

## Corrected CDS Result

| Metric | Result | Frozen requirement |
| --- | ---: | ---: |
| Valid runs | 6/6 | 6/6 |
| Paired wins | 1/3 | >= 2/3 |
| Median PSS | +668 KB | <= -4,096 KB |
| Median Private_Dirty | +736 KB | <= -1,024 KB |
| Median memory.current | -1,945,600 B | <= -1,048,576 B |
| Workload errors | 0 | 0 |
| V2-C | `MIXED_METRICS_NEEDS_RERUN` | `CONFIRMED_WIN` |
| V2-D | present | required |

The corrected artifact used separate variant-specific JSA archives, explicit
runtime mapping, an unchanged support JAR, and zero raw preservation failures.
The final CDS result therefore is not explained by a remaining JSA mismatch.
The evidence instead supports a CDS interaction with heap-page and anonymous
writable-page variance.

## Boundary

Patient CDS is not an admissible runtime promotion policy. The bounded no-CDS
confirmation is recorded in [patient-nocds-confirmation.md](patient-nocds-confirmation.md)
and does not erase this failure.
