# B0/V1/V2 Leg Diagnostics

All values are direct within-block deltas. Negative values favor the right-hand artifact.

| Service | Leg | PSS median / mean / MAD KB | PSS range KB | PSS wins | PSS 95% CI KB | Private Dirty median / wins | memory.current median / wins |
|---|---|---:|---:|---:|---|---:|---:|
| doctor-service | B0_TO_V1 | 654 / 1359.167 / 1850.5 | [-4328, 9104] | 3/6 | [-2571.5, 5995] | 498 / 3/6 | 1439744 / 1/6 |
| doctor-service | V1_TO_V2 | -6261.5 / -5389.5 / 1382.5 | [-8989, -1637] | 6/6 | [-7644, -2263] | -6108 / 6/6 | -13082624 / 6/6 |
| doctor-service | B0_TO_V2 | -4715.5 / -4030.333 / 1845.5 | [-7110, 2876] | 5/6 | [-7107, -268.5] | -4904 / 5/6 | -10874880 / 6/6 |
| patient-service | B0_TO_V1 | 910 / 578.167 / 4815.5 | [-7108, 6216] | 3/6 | [-5326.5, 6151] | 844 / 3/6 | 827392 / 3/6 |
| patient-service | V1_TO_V2 | -6135.5 / -3947.667 / 1790 | [-9224, 5194] | 5/6 | [-7925.5, 2218] | -6070 / 5/6 | -6387712 / 5/6 |
| patient-service | B0_TO_V2 | -1266.5 / -3369.5 / 4755 | [-13735, 5328] | 4/6 | [-12330.5, 3488.5] | -1238 / 4/6 | -1103872 / 4/6 |
| spring-petclinic-customers-service | B0_TO_V1 | 2516 / 3849 / 5389.5 | [-3979, 17336] | 3/6 | [-3508, 12539] | 2704 / 3/6 | 2658304 / 3/6 |
| spring-petclinic-customers-service | V1_TO_V2 | -3736 / -5598.5 / 3419.5 | [-22198, 4532] | 5/6 | [-14922, 1862.5] | -3926 / 5/6 | -3887104 / 5/6 |
| spring-petclinic-customers-service | B0_TO_V2 | -2947 / -1749.5 / 3178.5 | [-8171, 6935] | 4/6 | [-6516.5, 4215] | -2722 / 4/6 | -2703360 / 4/6 |

The JSON companion contains every block delta and complete statistics for PSS, Private_Dirty, and memory.current.
