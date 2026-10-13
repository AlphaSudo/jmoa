# Lambda and adapter cost engineering

This document retains the engineering lessons behind the historical overhead
investigations. It is not a delivery schedule or an authorization to benchmark.
See [current releases](../releases/current-state.md).

## What the shipped system does

Stable 2.1 profiles lambda sites, applies safety/profile admission, generates
required shared/package adapters, validates references after weaving and
materializes the deployed artifact. The final service needs no JMOA javaagent.
The separate raw reducer removes opt-in LVT/LVTT dependency metadata.

## Cost dimensions an adopter must inspect

- Static rewritten sites versus sites actually activated by a representative workload.
- Helper class count, physical duplication across roots and runtime-library identity.
- Lookup/support initialization, class loading, metaspace, code and allocation costs.
- Deployment packaging, archive/cache use, resident pages and native allocator state.
- Direct clean-B0 result alongside any incremental reducer comparison.

Fewer classfiles or a favorable incremental reducer delta cannot hide a direct
product regression. Preserve valid losing observations and measure costs under
the same frozen workload as memory.

## Current result boundaries

Stable 2.1's accepted PetClinic deployment saves 14.88 MiB PSS with CPU +14.71%
and startup +1.652 s inside frozen limits. Packaging is the dominant factorial
main effect; this is not proof of a standalone lambda-only win.

Provisional 2.15's Patient artifact has 788 rewritten static sites, 455 Tier 1
and 333 Tier 2, with 192 logical/195 physical helpers. The direct Leyden result
observes 8.38 MiB lower median stopped PSS and conditional lower bound 8.19 MiB.
Original calibration failed and used an approved exception; performance bounds
are unqualified or failed. Coverage and execution closeout do not establish
acceptable adapter economics or uploaded-binary parity.
