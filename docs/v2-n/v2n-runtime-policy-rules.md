# V2-N Runtime Policy Rules

`jmoa:recommend-runtime` is report-only. It evaluates existing evidence and
never changes the runtime command, image, packaging, or CDS archive.

| Condition | Decision |
| --- | --- |
| Exact registered protocol with artifact, smoke, V2-C, and V2-D gates | `RECOMMEND_CONFIRMED_POLICY` |
| Valid evidence but no exact registered protocol or missing V2-D | `RECOMMEND_SCREEN_REQUIRED` |
| Artifact evidence without semantic smoke in analyze mode | `ALLOW_ARTIFACT_ONLY` |
| Explicit diagnostic policy | `ALLOW_DIAGNOSTIC_ONLY` |
| Baseline and candidate use different runtime policies | `BLOCK_POLICY_MISMATCH` |
| CDS archive/artifact pair is missing, unmapped, or mismatched | `BLOCK_CDS_ARCHIVE_MISMATCH` |
| Required runtime stack is unavailable | `BLOCK_RUNTIME_STACK_MISSING` |
| Semantic smoke is absent in preflight or explicitly fails | `BLOCK_NO_SEMANTIC_SMOKE` |
| V2-C does not confirm the policy | `BLOCK_NO_V2C_CONFIRMATION` |
| Screen fails or regresses | `BLOCK_RUNTIME_PROMOTION` |

For CDS, archive equality means a registered archive fingerprint paired with
the registered artifact fingerprint. It does not mean an archive file hash
equals an artifact hash.

For no-CDS, V2-N requires explicit disabled-state evidence for CDS, AppCDS,
Leyden, and any runtime javaagent. A preflight also asks for an artifact
SHA-256 or deployment-layer fingerprint before it relies on a historical
no-CDS scope.
