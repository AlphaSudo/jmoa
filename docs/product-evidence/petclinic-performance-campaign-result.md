# PetClinic Audited Performance Campaign Result

Terminal verdict: **`ENVIRONMENT_VARIANCE_TOO_HIGH`**

The frozen PetClinic campaign executed the baseline runtime before any JMOA
candidate comparison. Two complete, unchanged B0 same-artifact campaigns
failed the frozen noise gate. The runner therefore stopped before V2 controls
or balanced B0/V2 pairs, exactly as required by the evidence contract.

## Frozen Inputs

- Source revision: `305a1f13e4f961001d4e6cb50a9db51dc3fc5967`
- Signed campaign SHA-256:
  `90CBB67E866F251164BA052C20EFC9F15DB0BC4A594D370DF32240836C5CA6C9`
- B0 artifact SHA-256:
  `88D4BC9F000A22041F8FD521A0442B85F9FC49966948FA11F642CBBABE0D6AE7`
- V2 artifact SHA-256:
  `338F5D44431E66B3EEC9B2CFAD6D9769D70D08E3C351B6FA221AE883D8E5A34B`
- Runtime: exploded Spring Boot application, `NO_CDS_LOW_DIRTY`,
  `MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden, and no runtime javaagent
- Workload per arm: 27 endpoints x 3 rounds, 81 requests

## Executed Baseline Controls

| Campaign run | Valid B0 arms | Workload | Pair absolute PSS | Median absolute PSS | Median absolute Private_Dirty | Median absolute memory.current | Signed second-minus-first PSS |
| --- | ---: | --- | --- | ---: | ---: | ---: | ---: |
| `20260723T190749Z` | 4/4 | 324/324 requests, 0 errors | 5,095 KB; 4,604 KB | 4,849.5 KB | 4,772 KB | 4,964,352 B | +4,849.5 KB |
| `20260723T192107Z` | 4/4 | 324/324 requests, 0 errors | 4,311 KB; 6,850 KB | 5,580.5 KB | 5,300 KB | 5,181,440 B | -5,580.5 KB |

Across both executions, all eight B0 arms passed health, workload, mutation,
artifact, and environment validation. The campaign produced 648 successful
requests and zero semantic errors.

The frozen noise limits were:

```text
median |delta PSS| <= 1,024 KB
median |delta Private_Dirty| <= 1,024 KB
median |delta memory.current| <= 2,097,152 bytes
```

Both executions exceeded all three limits. The signed positional direction
also reversed between executions. This is evidence of unstable
host/virtualization/order behavior, not a stable B0 memory value.

## Environment Evidence

Every pre-arm and post-arm Podman VM pressure gate passed:

- pre-arm available memory remained above the frozen 1 GiB minimum;
- swap used remained zero;
- memory PSI `some avg10` and `full avg10` remained zero;
- no workload, health, or semantic divergence occurred.

The first campaign's diagnostic attribution found a low-signal
heap-page-touch-like position effect with nearly flat heap-used and histogram
bytes. It did not establish retained-object growth or a product effect.

## What Did Not Run

The following stages were not admissible and were not executed:

- V2-to-V2 same-artifact controls;
- balanced B0/V2 product pairs;
- product V2-C confirmation;
- product V2-D attribution.

There is therefore no new PetClinic direct B0-to-V2 memory delta from this
campaign. Reporting one would mix an unqualified environment with a product
comparison that never ran.

## Command Ledgers

Each campaign has one chronological private Markdown command ledger containing
the exact commands, exit codes, stdout/stderr, HTTP responses, memory captures,
environment captures, and teardown. Child ledgers and raw responses are
integrity-indexed beneath the same run ID.

The retained run IDs are:

```text
petclinic-performance-campaign-20260723T190749Z
petclinic-performance-campaign-20260723T192107Z
```

Earlier pre-measurement launcher attempts are also retained privately. They
record fixture line-ending, PowerShell parameter-binding, and HTTP byte-decoding
harness failures. They produced no admissible memory comparison and are not
included in the eight valid B0 arms above.

## Claim Boundary

This result does not invalidate the separate V1-to-V2 engineering result or the
confirmed Doctor direct result. It closes the current PetClinic direct-product
campaign as **environment blocked** on the Windows-to-WSL-to-Podman host.

The frozen campaign must next run on a dedicated Linux host without changing
artifacts, workload, warmup, thresholds, JVM policy, or pair order. See the
[dedicated Linux handoff](dedicated-linux-campaign-handoff.md).
