# Doctor V1 Mechanism Activation Study

- Classification: **PROFILE_DERIVED_NON_CLAIM**
- Admitted decision sites: **542**
- Admitted sites observed in the recovered profile: **540 / 542** (99.63%)
- Exact Tier-1 supported sites observed: **255 / 255**
- Recovered profile invocation count across admitted sites: **26497**
- Workload decision: **PROFILE_COVERAGE_HIGH_RUNTIME_PHASE_UNATTRIBUTABLE**

| Family | Profile-observed sites | Profile invocation count | Median/site | P90/site |
|---|---:|---:|---:|---:|
| ACTUATOR_MICROMETER | 1 | 1 | 1 | 1 |
| DOCTOR_APPLICATION | 6 | 6 | 1 | 1 |
| FRAMEWORK_OTHER | 259 | 24709 | 1 | 96 |
| STARTUP_CONFIGURATION_AOT | 274 | 1781 | 1 | 1 |

The recovered profiler proves that the pre-rewrite training run observed nearly
all admitted site keys. It does **not** prove that the reconstructed runtime
diagnostic executed transformed branches. The historical profile has no phase
partition and reports a zero training duration, so startup, Actuator, metrics,
and business-path activity cannot be separated.

## Counters Not Captured

- transformed branch executions
- adapter instances
- adapter invocations
- fallback invocations
- allocations avoided
- workload-phase partition

This study is not included in the memory matrix and does not upgrade the
two-order reconstructed result into an exact historical replay.
