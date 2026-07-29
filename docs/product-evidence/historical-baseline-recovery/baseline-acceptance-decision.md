# Baseline Acceptance Decision

| Service | Decision | Proven issue | Corrected measurement |
|---|---|---|---|
| doctor-service | `REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR` | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` | `AUTHORIZED_AFTER_NEW_FREEZE` |
| patient-service | `B0_COMPARISON_INCONCLUSIVE` | `B0_RUNTIME_POLICY_MISMATCH` | `NOT_AUTHORIZED` |
| spring-petclinic-customers-service | `REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR` | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` | `AUTHORIZED_AFTER_NEW_FREEZE` |

No run was started. Conditional authorization is not permission to reuse an old image, archive, or workload.
