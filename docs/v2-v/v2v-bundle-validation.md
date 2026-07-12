# V2-V Bundle Validation

The validator accepts a target into cross-service ranking only when its
V2-U analyzer report is `MATCHED_DIAGNOSTIC_EVIDENCE` and all three stage
attribution files exist.

Current expected state from the available local evidence:

| Target | Expected status |
| --- | --- |
| customers | `EVIDENCE_INCOMPLETE` |
| visits | `EVIDENCE_INCOMPLETE` |
| doctor-d2r | `EVIDENCE_INCOMPLETE` |

The validator preserves the exact failure status when a partial report exists.
