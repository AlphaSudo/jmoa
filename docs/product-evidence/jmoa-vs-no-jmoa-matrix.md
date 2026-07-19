# JMOA Versus No-JMOA Product Matrix

Overall state: `DIRECT_PRODUCT_MATRIX_UNDER_RECONCILIATION`

The measurements below are retained. Artifact lineage passed, but historical
runtime replay and same-artifact variance did not admit a new three-arm block.
The aggregate verdict therefore remains provisional.

| Service | Policy | Status | Valid | Wins | Median PSS | PSS % | Private_Dirty | memory.current | Gate |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| petclinic-customers | `NO_CDS_LOW_DIRTY` | HISTORICAL_RUNTIME_DRIFT | 2/2 screen | 0/1 | 5446 KB | 1.62% | 5580 KB | 3059712 B | False |
| doctor-service | `APPLICATION_CDS` | CONFIRMED | 6/6 | 3/3 | -5809 KB | -1.77% | -5492 KB | -11845632 B | True |
| patient-service | `JDK_BASE_CDS_LOW_DIRTY` | SCREEN_ARTIFACT_MISMATCH | 2/2 screen | 0/1 | 3290 KB | 1.14% | 3336 KB | 3244032 B | False |

Direct measured comparisons only. No B0-to-V1 and V1-to-V2 arithmetic is used.
