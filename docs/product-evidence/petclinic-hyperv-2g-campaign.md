# PetClinic Hyper-V 2 GiB Campaign

## Scope

`HYPERV_DEBIAN_FIXED_2G` admits a fixed-memory Debian Hyper-V guest only after
the guest proves that it can run the frozen PetClinic campaign without swap,
sustained memory pressure, OOM activity, or inadequate headroom.

This profile changes host admission only. It does not change the B0 or V2
artifacts, OCI images, JVM options, GC, support services, workload, warmup,
settle time, noise limits, V2-C confirmation gates, or the strict 4 MiB gate.

## Frozen Runtime

- Four immutable OCI images: config, discovery, B0, and V2.
- `NO_CDS_LOW_DIRTY`, `MALLOC_ARENA_MAX=1`, and `-Xshare:off`.
- Serial GC and the existing frozen heap limits.
- Config and discovery support services remain present.
- 20-second warmup, 81-request workload, and 5-second settle.
- Claim captures precede perturbing diagnostics.

## Host Admission

| Gate | Threshold |
| --- | ---: |
| Guest `MemTotal` | at least 1,900 MiB |
| Logical processors | at least 4 |
| Preflight/idle `MemAvailable` | at least 1,350 MiB |
| Support-ready `MemAvailable` | at least 700 MiB |
| Pre-target `MemAvailable` | at least 600 MiB |
| In-arm/post-arm `MemAvailable` | at least 300 MiB |
| Swap | disabled; zero current use |
| Memory PSI `some/full avg10` | `0.00 / 0.00` |
| cgroup `oom` / `oom_kill` | zero |

The Windows Hyper-V configuration is a private operational prerequisite:
2,038 MiB fixed RAM, four vCPUs, Dynamic Memory disabled, and automatic
checkpoints disabled. The guest-side gates remain authoritative.

## Execution Order

1. Verify the pinned ED25519 host key and key authentication in an audited
   reconnect ledger. The earlier interruption is classified
   `HOST_HIBERNATION_INTERRUPTION`.
2. Commit the complete runner, run Gate A, and export one immutable package.
3. Import the package on persistent Debian storage. The portable package hash,
   `runnerRevision`, imported revision file, and fixture-bound script hashes
   must agree.
4. Capture the host fingerprint with
   `-HostProfile HYPERV_DEBIAN_FIXED_2G`.
5. Run `run-petclinic-performance-campaign.ps1 -DryRun`. No measured arm is
   permitted in this invocation.
6. Start a new full invocation. It must pass idle calibration, support-only
   calibration, and one `CAPACITY_QUALIFICATION_ONLY` B0 arm.
7. Run two reversed B0 controls. Stop on drift above the frozen noise limits.
8. Run two reversed V2 controls. Stop on drift above the same limits.
9. Run balanced product pairs in order B0-to-V2, V2-to-B0, B0-to-V2.
10. Run V2-C, the strict 4 MiB gate, and non-diagnostic V2-D.

Every external command, HTTP response, workload request, launch, capture, and
teardown is preserved in child ledgers and indexed by the parent campaign.

## Frozen Noise And Product Gates

The constrained profile does not loosen scientific thresholds:

- Same-artifact median absolute PSS drift: at most 1,024 KB.
- Same-artifact median absolute Private Dirty drift: at most 1,024 KB.
- Same-artifact median absolute `memory.current` drift: at most 2,097,152 B.
- V2-C: six valid arms, at least two paired wins, `CONFIRMED_WIN`, median PSS
  and Private Dirty at most -1,024 KB, median `memory.current` at most
  -1,048,576 B, and zero workload/semantic errors.
- Trusted product gate: median PSS at most -4,096 KB.

## Terminal Outcomes

The constrained campaign uses these evidence outcomes:

- `TRUSTED_PRODUCT_WIN`
- `CONFIRMED_PRODUCT_WIN_BELOW_4MIB`
- `PRODUCT_EFFECT_NOT_CONFIRMED`
- `ENVIRONMENT_VARIANCE_TOO_HIGH_2G`
- `STOPPED_CONSTRAINED_HOST_NOT_IDLE`
- `STOPPED_INSUFFICIENT_SUPPORT_STACK_HEADROOM`
- `STOPPED_INSUFFICIENT_2G_CAPACITY`
- `V2_ARTIFACT_RUNTIME_VARIANCE`
- `CAMPAIGN_INTERRUPTED_BY_HOST_POWER_EVENT`

A capacity, pressure, swap, OOM, or host-power stop is an environment outcome,
not a JMOA regression. Doctor and Patient are out of scope until PetClinic
reaches one terminal outcome.
