# Doctor Versus Non-Wins: V1 Cost Model

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

Doctor is the complete-product positive control, not a standalone V1 positive control.

| Service | V1 PSS KB | Heap PSS KB | Anonymous RW KB | Loaded classes | JMOA object bytes | Admitted logical sites | Exact activation | Direct verdict |
|---|---:|---:|---:|---:|---:|---:|---|---|
| doctor-service | 654 | -574 | 522 | -112.5 | 6288 | 542 | UNMEASURABLE_FROM_EXISTING_CAPTURES | COMPLETE_PRODUCT_WIN |
| patient-service | 910 | 104 | 94 | -8 | 96 | 549 | UNMEASURABLE_FROM_EXISTING_CAPTURES | B0_TO_V1_NOT_REPRODUCED |
| spring-petclinic-customers-service | 2516 | 1194 | 242 | -153 | 4752 | 310 | UNMEASURABLE_FROM_EXISTING_CAPTURES | B0_TO_V1_NOT_REPRODUCED |

Doctor does not demonstrate an independent V1 win. Its V2 reduction is large enough to overcome low-signal V1 overhead; current captures cannot determine whether higher transformed-site activation caused that difference.
