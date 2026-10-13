# 06 Prove Runtime Origin

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

Artifact construction does not prove that the JVM used the transformed code.

Before a performance claim, prove:

```text
expected artifact/image hash
materialized optimized libraries
no original shadowing
runtime artifact hash where available
loaded JMOA runtime/adapters
loaded transformed class samples
workload-level exercise
```

Use non-perturbing evidence first. Post-workload class histograms can prove live
adapter/runtime instances. The completed campaigns observed JMOA instances in
all six V1 histograms for each service:

```text
Doctor median: 393
Patient median: 6
PetClinic median: 297
```

That is family-level loading and exercise evidence. It is not a per-site
execution counter.

If existing evidence is insufficient, run one
`PERTURBED_DIAGNOSTIC_ONLY` session with class-load/origin tracing. Never put
that session into PSS medians.

## Decisions

```text
V1_RUNTIME_CONFIRMED
V1_PRESENT_NOT_LOADED
V1_LOADED_WRONG_ORIGIN
V1_LOADED_NOT_EXERCISED
V1_RUNTIME_SUPPORT_MISMATCH
```
