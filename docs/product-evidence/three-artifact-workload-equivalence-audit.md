# Three-Artifact Workload And Capture-Equivalence Audit

| Service | Decision | Requests | Rounds | Errors | Settle seconds | Timing violations | Path/status digests |
|---|---|---:|---:|---:|---:|---:|---:|
| doctor-service | WORKLOAD_EQUIVALENT | 600 | 3 | 0 | 5 | 0 | 1 |
| patient-service | WORKLOAD_EQUIVALENT | 600 | 3 | 0 | 5 | 0 | 1 |
| spring-petclinic-customers-service | WORKLOAD_EQUIVALENT | 81 | 3 | 0 | 5 | 0 | 1 |

Observed startup/workload durations are reported in JSON. Different elapsed time alone is not a capture defect when the nominal workload and settle policy are identical.
