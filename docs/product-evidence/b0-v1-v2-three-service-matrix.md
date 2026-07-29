# JMOA Direct B0 To V2 Three-Service Matrix

| Service | Runtime | Valid | Wins | Median PSS | 95% CI | Private Dirty | memory.current | Outcome |
|---|---|---:|---:|---:|---:|---:|---:|---|
| doctor-service | SPRING_BOOT_FAT_JAR / APPLICATION_CDS | 18/18 | 5/6 | -4715.5 KB | [-7107, -268.5] KB | -4904 KB | -10874880 B | COMPLETE_PRODUCT_WIN |
| patient-service | SPRING_BOOT_FAT_JAR / JDK_BASE_CDS_LOW_DIRTY | 18/18 | 4/6 | -1266.5 KB | [-12330.5, 3488.5] KB | -1238 KB | -1103872 B | B0_TO_V1_NOT_REPRODUCED |
| spring-petclinic-customers-service | EXPLODED_BOOT_APP / NO_CDS_LOW_DIRTY | 18/18 | 4/6 | -2947 KB | [-6516.5, 4215] KB | -2722 KB | -2703360 B | B0_TO_V1_NOT_REPRODUCED |

Launch criterion: **NOT PASSED** (1/3 complete product wins).

Every row is a direct within-runtime B0-to-V2 result. Historical medians and V1-to-V2 medians are not added to these values.
