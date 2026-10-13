# 12 Troubleshoot A Missing Product Effect

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

Do not rerun first. Audit in this order:

1. Artifact lineage.
2. V1 runtime origin and exercise.
3. Runtime flags, JDK, CDS/archive identity, and container limits.
4. Workload request/state equivalence.
5. Workload-to-capture timing.
6. B0-to-V1 memory attribution.
7. Artifact, block, and execution-position effects.

Allowed correction decisions:

```text
PROVEN_ARTIFACT_DEFECT
PROVEN_RUNTIME_ORIGIN_DEFECT
PROVEN_RUNTIME_POLICY_MISMATCH
PROVEN_CAPTURE_TIMING_DEFECT
PROVEN_WORKLOAD_MISMATCH
NO_CORRECTABLE_DEFECT_FOUND
```

Only a proven defect authorizes one corrected six-block campaign. Write a
correction contract first:

```text
defect and evidence
exact correction
unchanged dimensions
expected diagnostic consequence
stop condition
```

The Patient forensic audit found a real example: one workload completed more
than 31 hours before claim capture despite a 5-second declared settle. That is
a capture-timing defect, not a disappointing-result retry. The runner now
invalidates such a session. The one corrected campaign completed all 18 final
observations with fresh capture timing; it observed a `-1,266.5 KB` direct
median but still did not pass the product gate. No further Patient rerun is
authorized by this investigation.

If no defect is proven, the losing or uncertain result is authoritative.
Changing warmup, workload, policy, or artifact selection after seeing the
result creates a new experiment, not a correction.
