# Source Risk Audit

## Findings

1. Candidate public modules are real source modules, not generated evidence.
2. The original workspace is dirty and contains many private/generated phase
   artifacts.
3. The public copy removes known private patient-service-specific debug code.
4. The older agent/profiler root source is held back for manual review.
5. The repository is now licensed under Apache License 2.0.

## Verification

- Publication safety scan: PASS
- Default Maven build for `jmoa-runtime-lib` and `jmoa-maven-plugin`: PASS
- Launcher module: kept outside the default reactor until process-exit tests are
  split into a dedicated harness

## Current Risk Level

Low-to-moderate until:

- user reviews source inventory.
