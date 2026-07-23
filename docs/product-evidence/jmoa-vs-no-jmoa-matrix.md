# JMOA Versus No-JMOA Product Matrix

Overall state: `DIRECT_PRODUCT_MATRIX_INCOMPLETE_ENVIRONMENT_BLOCKED`

Doctor is confirmed. PetClinic's latest frozen campaign stopped before V2
after the clean B0 artifact failed same-artifact noise qualification twice.
Patient's accepted-artifact comparison remains open. No aggregate
three-service adoption verdict is available.

| Service | Policy | Status | Valid evidence | Wins | Median PSS | Private_Dirty | memory.current | Gate |
| --- | --- | --- | ---: | ---: | ---: | ---: | ---: | --- |
| petclinic-customers | `NO_CDS_LOW_DIRTY` | `ENVIRONMENT_VARIANCE_TOO_HIGH` | 8/8 B0 control arms | n/a | n/a | n/a | n/a | Not admitted |
| doctor-service | `APPLICATION_CDS` | `CONFIRMED` | 6/6 product runs | 3/3 | -5,809 KB | -5,492 KB | -11,845,632 B | Passed |
| patient-service | `JDK_BASE_CDS_LOW_DIRTY` | `SCREEN_ARTIFACT_MISMATCH` | 2/2 screen runs | 0/1 | +3,290 KB | +3,336 KB | +3,244,032 B | Failed |

Direct measured comparisons only. No B0-to-V1 and V1-to-V2 arithmetic is used.

For PetClinic, “n/a” is intentional. Two same-artifact B0 campaigns produced
median absolute PSS noise of `4,849.5 KB` and `5,580.5 KB`, above the frozen
`1,024 KB` limit. The positional direction reversed between campaigns while
all eight arms completed 81 requests with zero errors and passed swap/PSI
gates. V2 controls and product pairs did not run. See the
[campaign result](petclinic-performance-campaign-result.md).
