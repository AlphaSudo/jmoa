# Historical Versus Current B0: Absolute Audit

Read-only identity and absolute-footprint audit. It does not replace the sealed current matrix.

| Service | Historical PSS KB | Current PSS KB | Current - historical KB | Artifact exact | Decision |
|---|---:|---:|---:|---|---|
| doctor-service | 336484 | 328195.5 | -8288.5 | False | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` |
| patient-service | 338832 | 295891 | -42941 | NOT_RECOVERED | `B0_RUNTIME_POLICY_MISMATCH` |
| spring-petclinic-customers-service | 351779 | 344262 | -7517 | False | `B0_SOURCE_OR_DEPENDENCY_MISMATCH` |

Absolute drift is descriptive only where source/runtime identity differs; it is not an optimizer delta.
