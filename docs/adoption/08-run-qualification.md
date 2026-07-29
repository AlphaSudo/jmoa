# 08 Run Qualification

Qualification is a three-session runtime and semantic gate:

```text
B0 -> V1 -> V2
```

Each session must start a fresh target and the required support stack, wait for
health, warm up, execute the full workload, settle, and capture evidence.

Qualification checks:

```text
correct artifact and image
runtime policy proof
health UP
exact request count
zero workload errors
semantic/state checks
environment pressure and OOM checks
required evidence files
workload-to-capture timing
```

Qualification deltas are diagnostic only. They do not authorize a win claim,
set a favorable order, or replace the six balanced blocks.

An interrupted attempt remains on disk with its ledger. The generic runner
permits at most two new attempts for a genuinely invalid session. A valid
losing qualification is not rerun.

## Capture-Timing Gate

The first claim snapshot must occur after workload completion and no later than
`settleSeconds + 60 seconds` unless a stricter frozen value is configured.
This gate prevents a recovered, long-lived JVM from entering campaign medians.
