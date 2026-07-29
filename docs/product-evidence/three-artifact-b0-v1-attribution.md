# B0 To V1 Memory Attribution

| Service | Classification | PSS KB | Heap PSS KB | Heap used KB | Histogram bytes | Classes | Metaspace committed KB | Code committed KB |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| doctor-service | B0_V1_NO_MEASURABLE_EFFECT | 654 | 0 | -60 | -40784 | -130 | -92 | -88 |
| patient-service | B0_V1_NO_MEASURABLE_EFFECT | 910 | 560 | 76 | 77756 | -5 | 1 | 49 |
| spring-petclinic-customers-service | B0_V1_RUNTIME_OVERHEAD_EXCEEDS_SAVING | 2516 | 1706 | 253 | -146576 | -174 | 88 | 214 |

Doctor and Patient are low-signal rather than standalone V1 wins. PetClinic reduces loaded classes, but heap page touch/runtime overhead exceeds that saving.
