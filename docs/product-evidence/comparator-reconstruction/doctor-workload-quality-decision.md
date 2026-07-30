# Doctor Workload Quality Decision

- Decision: **PROFILE_COVERAGE_HIGH_RUNTIME_PHASE_UNATTRIBUTABLE**
- Historical profile coverage: **540 / 542 admitted sites**
- Exact Tier-1 profile coverage: **255 / 255 sites**
- Reconstructed workload: **60 Actuator requests + 20 `/doctors` requests**

The recovered pre-rewrite training profile shows broad admitted-site coverage,
but it does not partition startup, Actuator, metrics, and business phases. It
also cannot prove post-rewrite branch or adapter execution. The reconstructed
80-request runtime workload is therefore adequate for the bounded two-order
diagnostic, but inadequate for explaining V1 mechanism ROI.

The next mechanism step is instrumentation, not another memory campaign:
opt-in counters must report transformed branches, adapter instances and
invocations, fallbacks, and workload phase.
