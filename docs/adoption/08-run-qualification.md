# 08 Run Qualification

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

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
