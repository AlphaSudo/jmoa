# Historical Versus Current V1 Identity

| Service | Artifact exact | Decision | Qualification |
|---|---|---|---|
| doctor-service | True | `V1_EXACT_IDENTITY` | JAR exact; application CDS archive not exact, so runtime identity is not exact. |
| patient-service | False | `V1_IDENTITY_INCONCLUSIVE` | Phase 31D candidate JAR not recovered; a later corrected candidate cannot substitute for it. |
| spring-petclinic-customers-service | True | `V1_EXACT_IDENTITY` | Historical Phase 33M full-P2 JAR is the exact finalized current V1 artifact. |
