# Final B0/V1/V2 Forensic Matrix

| Service | B0 -> V1 PSS | V1 -> V2 PSS | B0 -> V2 PSS | Wins | 95% CI | Verdict |
|---|---:|---:|---:|---:|---|---|
| doctor-service | 654 KB | -6261.5 KB | -4715.5 KB | 5/6 | [-7107, -268.5] | COMPLETE_PRODUCT_WIN |
| patient-service | 910 KB | -6135.5 KB | -1266.5 KB | 4/6 | [-12330.5, 3488.5] | B0_TO_V1_NOT_REPRODUCED |
| spring-petclinic-customers-service | 2516 KB | -3736 KB | -2947 KB | 4/6 | [-6516.5, 4215] | B0_TO_V1_NOT_REPRODUCED |

Doctor B0_TO_V1 is not a win; Doctor succeeds because its V1_TO_V2 reduction is large enough to create a direct B0_TO_V2 win.
