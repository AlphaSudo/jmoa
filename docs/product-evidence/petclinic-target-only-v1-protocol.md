# PETCLINIC_TARGET_ONLY_V1

## Status

Pre-registered before any target result.

`SUPPORT_CALIBRATION_V2` remains valid diagnostic evidence with outcome
`SUPPORT_STACK_PRIVATE_MEMORY_UNSTABLE`. It established that config/discovery
continued warming; it did not establish that customers-service B0 and V2 were
incomparable.

## Measurement Boundary

The product subject is Spring PetClinic `customers-service`.

Claim metrics:

- target process PSS;
- target process Private_Dirty;
- exact target-container `memory.current`;
- exact target-container `memory.stat` anonymous and file bytes.

Config/discovery memory, the parent user slice, whole-VM memory, Windows host
memory, the runner, and SSH processes are diagnostic only and never enter the
B0/V2 product delta.

## Frozen Execution

- One config/discovery support stack per pair.
- Health passes, then a fixed 180-second support settle.
- Support admission requires health, no restarts, no swap, no OOM, PSI full
  `avg10 = 0`, at least 700 MiB available, and frozen image/config/JDK proof.
- Target warmup remains 20 seconds.
- The corrected workload remains 27 endpoints by 3 rounds (81 requests).
- Target post-workload settle remains 5 seconds.
- `NO_CDS_LOW_DIRTY`, `MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden, and no
  runtime javaagent remain unchanged.

## Order

1. One non-evidence B0 capacity arm.
2. B0 same-artifact controls: B0-A to B0-B, then reversed.
3. V2 same-artifact controls: V2-A to V2-B, then reversed.
4. Product pairs: B0 to V2, V2 to B0, B0 to V2.

Support remains running between the two target arms in each pair. Each target
uses a unique Eureka instance ID. Between arms, the runner proves the first
container and process are absent, support remains healthy and restart-free,
and the first registration was removed or identity-isolated.

## Frozen Gates

Same-artifact median absolute drift:

- PSS at most 1,024 KB;
- Private_Dirty at most 1,024 KB;
- target `memory.current` at most 2,097,152 bytes.

V2-C product confirmation:

- six of six target arms valid;
- at least two of three paired wins;
- median target PSS at most -1,024 KB;
- median target Private_Dirty at most -1,024 KB;
- median target `memory.current` at most -1,048,576 bytes;
- zero semantic errors;
- `CONFIRMED_WIN`.

Strict product gate: median target PSS at most -4,096 KB.

Terminal outcomes are `TRUSTED_PRODUCT_WIN`,
`CONFIRMED_PRODUCT_WIN_BELOW_4MIB`, `PRODUCT_EFFECT_NOT_CONFIRMED`,
`TARGET_B0_RUNTIME_VARIANCE`, `TARGET_V2_RUNTIME_VARIANCE`,
`STOPPED_INSUFFICIENT_2G_TARGET_CAPACITY`, or
`CAMPAIGN_INTERRUPTED_BY_HOST_POWER_EVENT`.

No threshold, pair-count, workload, warmup, settle, heap-policy, support
topology, or artifact redesign is allowed after target evidence begins.

## Authoritative Result

The frozen protocol was executed on 2026-07-26. It terminated at the B0
same-artifact reproducibility gate with `TARGET_B0_RUNTIME_VARIANCE`; V2
controls and product pairs were not run. See
[PETCLINIC_TARGET_ONLY_V1 Result](petclinic-target-only-v1-result.md).
