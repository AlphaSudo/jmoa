# PetClinic direct target-cgroup RAM result

## Decision

- Target-cgroup RAM claimable: **True**
- R41F promotion authorized: **True**
- Terminal decision: `T7R2_CGROUP_RAM_WIN_PROMOTED`
- Failed gates: **none**

This is a fresh exact R41F-minus-strict-B0 result with symmetric fat-JAR
packaging. It claims target-cgroup total RAM, not a 4 MiB process-PSS win. The
completed T7R process-PSS nonclaim remains unchanged.

## Fresh result

- `memory.current`: **-3317760 bytes**, 12/12 favorable, exact `p=0.00048828125`.
- `memory.current` bootstrap 95%: **[-3715072, -2932736] bytes**.
- Process PSS: **-1407.5 KiB**, bootstrap upper **-638.5 KiB**.
- Cgroup anon/file: **-1339392 / -2113536 bytes**.
- Native process `[heap]` PSS: **2202 KiB**.
- NMT total committed: **-2928.5 KiB**.

The 2 MiB median materiality floor is twice the prospectively frozen 1 MiB
memcg charge-stock floor. The one-sided 95% upper bound also had to be at least
one complete floor below zero. No T7R observations entered this inference.