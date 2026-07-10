# V2-M Admission States

V2-M uses stable decisions and records public/private/internal confirmation as a
separate scope. For example, both Doctor and visits can be
`RECOMMEND_CONFIRMED` while retaining `PRIVATE` and `PUBLIC` scope respectively.

| State | Meaning |
| --- | --- |
| `RECOMMEND_CONFIRMED` | All raw artifact, semantic, V2-C, V2-D, and exact-protocol gates passed |
| `RECOMMEND_SCREEN_REQUIRED` | Artifact/semantic evidence is acceptable, but this protocol needs a fresh runtime screen or confirmation |
| `ALLOW_ARTIFACT_ONLY` | Artifact and raw audit gates passed; semantic runtime evidence is unavailable |
| `BLOCK_UNSAFE` | Raw audit drift or unsupported stripped/requested attributes were found |
| `BLOCK_SEMANTIC_FAILURE` | The candidate failed semantic smoke |
| `BLOCK_NO_EVIDENCE` | Required artifact, raw audit, semantic, or attribution evidence is missing |
| `BLOCK_RUNTIME_PROMOTION` | A screen failed or V2-C did not confirm a win |
| `DIAGNOSTIC_ONLY` | The supplied runtime protocol is explicitly diagnostic |
| `UNKNOWN` | Evidence is structurally valid but outside the raw reducer admission model |

Only `RECOMMEND_CONFIRMED` sets `runtimePromotionAllowed=true`, and only when the
current service, launch mode, and runtime policy match the confirmation.
