# petclinic Comparator Entry Audit

- Decision: **HISTORICAL_BASELINE_CONTAMINATED**
- Historical artifact SHA-256: `4952EF9306C732846BFAE0FAE6A67BE2F9B8509644B3396B8247215A03E5589D`
- Current artifact SHA-256: `2F4A63A803B2A05050ACF020AD382ABE589C52EE50253A1F5DC85F6D62584ACF`
- Entry differences: **10**
- Disqualifying differences: **2**
- Historical JMOA entries: **1**
- Current JMOA entries: **0**

Logical paths are deliberately omitted. Stable SHA-256 path identifiers are retained in the JSON report.

## Categories

| Category | Count |
|---|---:|
| APPLICATION_CLASS | 7 |
| MANIFEST | 1 |
| METADATA | 2 |

## Classifications

| Classification | Count |
|---|---:|
| BENIGN_CLASSFILE_NONDETERMINISM | 5 |
| DISQUALIFYING_COMPARATOR_CONTAMINATION | 1 |
| DISQUALIFYING_COMPARATOR_DRIFT | 1 |
| EXPECTED_NONFUNCTIONAL_BUILD_DRIFT | 3 |

This is an artifact-comparator audit, not runtime evidence. A disqualifying result requires exact historical comparator reuse or a new clean baseline campaign.
