# V2-S Recommendation Replay

- Cases: `14`
- Passed: `14`
- Failed: `0`

| Case | Expected | Actual | Scope | Protocol match | Passed |
| --- | --- | --- | --- | --- | --- |
| `v2i-petclinic-customers-raw-confirmed` | `RECOMMEND_CONFIRMED` | `RECOMMEND_CONFIRMED` | `PUBLIC` | `true` | `true` |
| `v2k-doctor-raw-confirmed-private` | `RECOMMEND_CONFIRMED` | `RECOMMEND_CONFIRMED` | `PRIVATE` | `true` | `true` |
| `v2l-petclinic-visits-raw-confirmed` | `RECOMMEND_CONFIRMED` | `RECOMMEND_CONFIRMED` | `PUBLIC` | `true` | `true` |
| `v2h-petclinic-hardened-asm-screen-failed` | `BLOCK_RUNTIME_PROMOTION` | `BLOCK_RUNTIME_PROMOTION` | `PUBLIC` | `false` | `true` |
| `v2j-doctor-raw-artifact-only` | `ALLOW_ARTIFACT_ONLY` | `ALLOW_ARTIFACT_ONLY` | `PRIVATE` | `false` | `true` |
| `v2q-visits-application-confirmation-failed` | `BLOCK_RUNTIME_PROMOTION` | `BLOCK_RUNTIME_PROMOTION` | `PUBLIC` | `false` | `true` |
| `v2r-application-low-roi-artifact-only` | `APPLICATION_LOW_ROI_ARTIFACT_ONLY` | `APPLICATION_LOW_ROI_ARTIFACT_ONLY` | `PUBLIC` | `false` | `true` |
| `v2r-application-medium-roi-screen-required` | `APPLICATION_SCREEN_REQUIRED` | `APPLICATION_SCREEN_REQUIRED` | `PUBLIC` | `false` | `true` |
| `v2r-generated-surface-report-only` | `GENERATED_REPORT_ONLY` | `GENERATED_REPORT_ONLY` | `PUBLIC` | `false` | `true` |
| `v2r-generated-high-roi-prototype-candidate` | `CANDIDATE_FOR_PROTOTYPE` | `CANDIDATE_FOR_PROTOTYPE` | `PUBLIC` | `false` | `true` |
| `v2r-generated-unsafe-mutation-blocked` | `GENERATED_MUTATION_BLOCKED` | `GENERATED_MUTATION_BLOCKED` | `PUBLIC` | `false` | `true` |
| `v2s-static-large-family-report-only` | `GENERATED_REPORT_ONLY` | `GENERATED_REPORT_ONLY` | `PUBLIC` | `false` | `true` |
| `v2s-runtime-relevant-unsafe-family-blocked` | `GENERATED_MUTATION_BLOCKED` | `GENERATED_MUTATION_BLOCKED` | `PUBLIC` | `false` | `true` |
| `v2s-bounded-safe-prototype-candidate` | `CANDIDATE_FOR_PROTOTYPE` | `CANDIDATE_FOR_PROTOTYPE` | `PUBLIC` | `false` | `true` |
