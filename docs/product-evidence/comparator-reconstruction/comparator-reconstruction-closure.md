# Historical Comparator Reconstruction Closure

Status: **CLOSED_WITHOUT_NEW_PRODUCT_CLAIM**

| Service | Decision | Performance campaign | Current direct B0->V2 PSS |
|---|---|---|---:|
| doctor-service | `DOCTOR_HISTORICAL_V1_NOT_REPRODUCED` | False | -4715.5 KB |
| patient-service | `PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE` | False | -1266.5 KB |
| spring-petclinic-customers-service | `PETCLINIC_HISTORICAL_B0_CONTAMINATED` | False | -2947 KB |

Doctor reproduced the historical anonymous/private-dirty B0 range, but its exact historical V1 artifact did not reproduce the historical direction.
PetClinic historical B0 is contaminated by JMOA output and semantic application drift.
Patient lacks the historical B0/source/support tuple required for a valid reconstruction.

No six-order historical reconstruction campaign is authorized. The current sealed matrix remains authoritative for its own artifacts and protocol.
