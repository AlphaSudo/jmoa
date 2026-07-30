# Historical Expected Engineering Budget

These numbers are labeled `HISTORICAL_EXPECTED_ENGINEERING_BUDGET`. They are not `CURRENT_DIRECT_PRODUCT_EFFECT` measurements and must not be added across campaigns.

| Service | Historical directional sum | Current direct B0->V2 | Reconstruction decision |
|---|---:|---:|---|
| doctor-service | -7884 KB | -4715.5 KB | `V1_RUNTIME_COST` |
| patient-service | -12591 KB | -1266.5 KB | `PATIENT_HISTORICAL_COMPARATOR_NOT_RECOVERABLE` |
| spring-petclinic-customers-service | -10770 KB | -2947 KB | `HISTORICAL_PETCLINIC_B0_INVALID` |

Doctor's old published `-6,048 KB` figure was a median-calculation error. The corrected historical independent-median delta is `-2,728 KB`; the reconstructed two-order artifact estimate is `+5,401 KB` and remains timing/provenance scoped.

PetClinic's historical baseline is contaminated. Patient's historical comparator tuple is not recoverable. No row authorizes a new performance campaign.

Medians from separate campaigns are not additive. Historical budgets are engineering diagnostics only; current direct B0-to-V2 results are the product evidence.
