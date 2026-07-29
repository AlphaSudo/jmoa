# 06 Prove Runtime Origin

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
