# Patient Original Campaign Defect Audit

The original Patient campaign is preserved but excluded from the final product
matrix because one session has a proven capture-timing defect.

- Protocol: `PATIENT_B0_V1_V2_BALANCED_V1`
- Original final sessions: 18/18 marked valid
- Invalidated session: `block-1-position-1-b0`
- Workload completed: `2026-07-27T10:02:48.7365524Z`
- First claim snapshot: `2026-07-28T17:28:58Z`
- Actual lag: `113,169.263` seconds
- Declared settle: 5 seconds
- Corrected-run ceiling: 65 seconds

The campaign's 44 command-ledger files, 311 analysis files, and 19,777 raw
evidence files are cryptographically sealed in the JSON companion. Raw evidence
and private configuration remain outside Git.

Exactly one corrected Patient campaign is authorized. Artifacts, workload,
runtime policy, six permutations, product thresholds, and the prohibition on
result-driven retries remain unchanged.
