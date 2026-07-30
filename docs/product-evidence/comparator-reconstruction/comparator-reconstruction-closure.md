# Historical Comparator Reconstruction Closure

Status: **CLOSED_WITHOUT_NEW_PRODUCT_CLAIM**

| Service | Decision | Performance campaign | Current direct B0->V2 PSS |
|---|---|---|---:|
| doctor-service | `V1_RUNTIME_COST` | False | -4715.5 KB |
| patient-service | `PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE` | False | -1266.5 KB |
| spring-petclinic-customers-service | `HISTORICAL_PETCLINIC_B0_INVALID` | False | -2947 KB |

Doctor V1 was more expensive in both reconstructed orders. This supports a reconstructed-runtime V1 cost, not an exact historical replay.
PetClinic historical B0 is invalid because it contains JMOA output and semantic application drift.
Patient lacks the historical B0/source/support tuple required for a valid reconstruction.

No six-order historical reconstruction campaign is authorized. The current sealed matrix remains authoritative for its own artifacts and protocol.

The [command ledger index](doctor-command-ledger-index.md) records complete and failed scenario attempts. Raw commands and responses remain private; sanitized summaries and hashes are published here.
