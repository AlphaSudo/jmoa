# Baseline Acceptance Decision

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

| Service | Decision | Proven issue | Corrected measurement |
|---|---|---|---|
| doctor-service | `REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR` | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` | `AUTHORIZED_AFTER_NEW_FREEZE` |
| patient-service | `B0_COMPARISON_INCONCLUSIVE` | `B0_RUNTIME_POLICY_MISMATCH` | `NOT_AUTHORIZED` |
| spring-petclinic-customers-service | `REJECT_CURRENT_B0_AS_HISTORICAL_COMPARATOR` | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` | `AUTHORIZED_AFTER_NEW_FREEZE` |

No run was started. Conditional authorization is not permission to reuse an old image, archive, or workload.
