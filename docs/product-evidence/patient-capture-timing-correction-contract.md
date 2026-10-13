# Patient Capture-Timing Correction Contract

**Historical experiment record.** Measurements and dispositions below belong
to their original artifact/runtime protocol. Current publication boundaries are
[stable 2.1 and provisional 2.15](../releases/current-state.md); older medians are not added to those
results and older verdicts do not replace them.

The original Patient campaign contains one concrete validity defect:
`block-1-position-1-b0` completed its workload at
`2026-07-27T10:02:48.7365524Z`, but its first claim snapshot was not captured
until `2026-07-28T17:28:58Z`.

The actual workload-to-capture lag was `113,169.263` seconds. The declared
settle was 5 seconds, and every other final Patient session captured within
13.4 to 25.8 seconds of workload completion.

## Exact Correction

The generic three-artifact runner now rejects a session when the first
post-workload snapshot:

- predates workload completion; or
- occurs later than `settleSeconds + 60 seconds`.

The failed attempt and its complete command ledger remain preserved. The
runner may create at most two fresh retry directories for an invalid session.

## Frozen Dimensions

Artifacts, hashes, JDK policy, six permutations, qualification order,
600-request workload, warmup, nominal settle, product gates, and the rule
against replacing valid losing runs do not change.

This authorizes exactly one corrected Patient campaign. It does not authorize
result-driven retries, and it makes no prediction about the performance
direction.
