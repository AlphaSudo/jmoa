# Product evidence

## Current publications

| Record | Status | Read with the number |
| --- | --- | --- |
| [PetClinic 2.1](petclinic-r41f-b0-t7r23-result.md) / [JSON](petclinic-r41f-b0-t7r23-result.json) | Accepted stable whole-deployment result | 14.88 MiB PSS; 16.24 MiB cgroup; packaging-inclusive; CPU +14.71%, startup +1.652 s within frozen gates |
| [Patient 2.15](patient-scoped-ram-benchmark.md) / [JSON](patient-scoped-ram-benchmark.json) | Provisional benchmark evidence, no new Maven binaries | 8.38 MiB median stopped PSS; conditional lower bound 8.19 MiB; original calibration failed with approved exception; costs unqualified or failed |

Patient startup/cache warmth, production parity and uploaded-binary parity
remain unqualified. Inspect its complete 82-metric cost table, identities and
limitations before interpreting the RAM result.

## Historical experiments

Other records preserve earlier protocols, investigations and negative outcomes.
Their JSON data is retained under its original schema and comparator; the
Markdown reading context distinguishes it from current release evidence.
The [older three-service matrix](../results/v2-three-service-matrix.md) is an
engineering-evolution record, not the current stable PetClinic or provisional
Patient direct comparison. Internal service implementation ledgers and working
plans are outside this public tree.

See [release boundaries](../releases/current-state.md) and the
[technical papers](../paper/jmoa-v2.15-patient-leyden-memory-evidence.md).
