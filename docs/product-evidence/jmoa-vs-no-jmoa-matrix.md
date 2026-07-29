# JMOA Versus No-JMOA Product Matrix

Overall state: `DIRECT_PRODUCT_MATRIX_INCOMPLETE_ENVIRONMENT_BLOCKED`

Doctor is confirmed. PetClinic's final independent-session protocol stopped
before V2 after the clean B0 artifact failed the frozen repeatability gate.
Patient's accepted-artifact comparison remains open. No aggregate
three-service adoption verdict is available.

| Service | Policy | Status | Valid evidence | Wins | Median PSS | Private_Dirty | memory.current | Gate |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| petclinic-customers | `NO_CDS_LOW_DIRTY` | `PETCLINIC_DIRECT_PRODUCT_UNMEASURABLE_ON_CURRENT_HOST` | 3/3 independent B0 sessions | n/a | n/a | n/a | n/a | Permanently stopped on current VM |
| doctor-service | `APPLICATION_CDS` | `CONFIRMED` | 6/6 product runs | 3/3 | -5,809 KB | -5,492 KB | -11,845,632 B | Passed |
| patient-service | `JDK_BASE_CDS_LOW_DIRTY` | `SCREEN_ARTIFACT_MISMATCH` | 2/2 screen runs | 0/1 | +3,290 KB | +3,336 KB | +3,244,032 B | Failed |

Direct measured comparisons only. No B0-to-V1 and V1-to-V2 arithmetic is used.

For PetClinic, “n/a” is intentional. The final protocol gave every B0 target a
fresh support lifecycle. All three sessions completed 81 requests with zero
errors, but their PSS, Private_Dirty, and `memory.current` ranges were `3,038`
KB, `2,912` KB, and `3,321,856` bytes, above the frozen limits. V2
qualification and product sessions did not run. See the
[independent-session result](petclinic-independent-session-v1-result.md).
