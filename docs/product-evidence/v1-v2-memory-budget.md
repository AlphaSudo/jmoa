# V1/V2 Memory Budget

| Service | V1 PSS KB | V2 PSS KB | Leg sum KB | Direct B0->V2 KB | Remainder KB |
|---|---:|---:|---:|---:|---:|
| doctor-service | 654 | -6261.5 | -5607.5 | -4715.5 | 892 |
| patient-service | 910 | -6135.5 | -5225.5 | -1266.5 | 3959 |
| spring-petclinic-customers-service | 2516 | -3736 | -1220 | -2947 | -1727 |

Medians of paired legs are not algebraically additive. The remainder is diagnostic and combines interaction, order, and sampling variance.
