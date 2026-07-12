# V2-M Historical Recommendation Replay

Status:

```text
PASSED
```

The replay ran through the `jmoa:recommend-reducer` Maven goal with mismatch
failure enabled.

```text
cases: 6
passed: 6
failed: 0
```

| Case | Expected | Actual | Scope | Protocol match |
| --- | --- | --- | --- | --- |
| V2-I customers raw confirmed | `RECOMMEND_CONFIRMED` | `RECOMMEND_CONFIRMED` | `PUBLIC` | `true` |
| V2-K Doctor raw confirmed | `RECOMMEND_CONFIRMED` | `RECOMMEND_CONFIRMED` | `PRIVATE` | `true` |
| V2-L visits raw confirmed | `RECOMMEND_CONFIRMED` | `RECOMMEND_CONFIRMED` | `PUBLIC` | `true` |
| V2-H hardened ASM screen failed | `BLOCK_RUNTIME_PROMOTION` | `BLOCK_RUNTIME_PROMOTION` | `PUBLIC` | `false` |
| V2-J Doctor raw artifact-only | `ALLOW_ARTIFACT_ONLY` | `ALLOW_ARTIFACT_ONLY` | `PRIVATE` | `false` |
| V2-Q visits application confirmation failed | `BLOCK_RUNTIME_PROMOTION` | `BLOCK_RUNTIME_PROMOTION` | `PUBLIC` | `false` |

The replay demonstrates that the model does not bias toward promotion: a known
failed screen stays blocked, a failed application-class confirmation stays
blocked, and artifact-only evidence stays artifact-only.

These are normalized regression fixtures, not substitutes for raw run archives
or a reconstruction of phase chronology. In particular, the V2-I case combines
its runtime confirmation with the V2-J productized raw-auditor gate now required
by V2-M.
