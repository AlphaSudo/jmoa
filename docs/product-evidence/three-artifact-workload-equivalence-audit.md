# Three-Artifact Workload And Capture-Equivalence Audit

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

| Service | Decision | Requests | Rounds | Errors | Settle seconds | Timing violations | Path/status digests |
|---|---|---:|---:|---:|---:|---:|---:|
| doctor-service | WORKLOAD_EQUIVALENT | 600 | 3 | 0 | 5 | 0 | 1 |
| patient-service | WORKLOAD_EQUIVALENT | 600 | 3 | 0 | 5 | 0 | 1 |
| spring-petclinic-customers-service | WORKLOAD_EQUIVALENT | 81 | 3 | 0 | 5 | 0 | 1 |

Observed startup/workload durations are reported in JSON. Different elapsed time alone is not a capture defect when the nominal workload and settle policy are identical.
