# Results by publication scope

- **Stable 2.1 PetClinic:** [accepted direct result](../product-evidence/petclinic-r41f-b0-t7r23-result.md), 14.88 MiB lower PSS and 16.24 MiB lower target-cgroup memory; packaging-inclusive with passing frozen cost gates.
- **Provisional 2.15 Patient:** [scoped benchmark](../product-evidence/patient-scoped-ram-benchmark.md), 8.38 MiB lower median stopped PSS and conditional 8.19 MiB lower bound. Original calibration failed and used an approved exception; performance bounds unqualified or failed. Startup/cache warmth and production/uploaded-binary parity unqualified; no new Maven binaries.
- **Historical research:** [three-service incremental matrix](v2-three-service-matrix.md), [V1/V2 evolution](v1-vs-v2-evolution-matrix.md), and [negative results](negative-results.md).

Results have separate comparators and acceptance contracts. Do not sum effects
across services, historical campaigns or incremental legs.
