# V2 Three-Service Memory Matrix

Overall status: **BLOCKED_FINAL_ACCEPTANCE**

Comparison: `final V1 -> final V2`

| Service | Status | Valid runs | Paired wins | Median PSS KB | Median Private_Dirty KB | Median memory.current bytes | V2-C | V2-D |
|---|---:|---:|---:|---:|---:|---:|---|---|
| petclinic-customers | PASS | 6/6 | 2/3 | -6012 | -5708 | -8081408 | CONFIRMED_WIN | True |
| doctor | PASS | 6/6 | 3/3 | -5156 | -5212 | -6975488 | CONFIRMED_WIN | True |
| patient | FAIL | 6/6 | 1/3 | 1652 | 1756 | -774144 | MIXED_METRICS_NEEDS_RERUN | True |

## Gate

- 6/6 valid runs per service
- at least 2/3 paired wins
- median PSS <= -4096 KB
- median Private_Dirty <= -1024 KB
- median memory.current <= -1048576 bytes
- zero workload and semantic errors
- V2-C `CONFIRMED_WIN` and V2-D attribution

Raw private evidence is intentionally excluded from this repository. A pending or failed row blocks the aggregate claim.
