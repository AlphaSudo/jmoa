# Dedicated Linux Campaign Handoff

The PetClinic direct-product campaign must move to a dedicated Linux host. The
Windows-to-WSL-to-Podman environment passed semantic and pressure validation
but failed the frozen same-artifact B0 noise gate twice.

## Immutable Inputs

Do not rebuild or substitute the measured artifacts.

```text
campaign SHA-256:
90CBB67E866F251164BA052C20EFC9F15DB0BC4A594D370DF32240836C5CA6C9

B0 artifact SHA-256:
88D4BC9F000A22041F8FD521A0442B85F9FC49966948FA11F642CBBABE0D6AE7

V2 artifact SHA-256:
338F5D44431E66B3EEC9B2CFAD6D9769D70D08E3C351B6FA221AE883D8E5A34B
```

The signed private manifest also pins the config-server, discovery-server, B0,
and V2 image IDs. Verify every digest before runtime.

## Host Requirements

- dedicated Linux host or dedicated Linux VM without a nested desktop
  container VM;
- no unrelated containers or benchmark workloads;
- required ports free;
- at least 1 GiB available before every arm;
- zero swap used before and after every arm;
- memory PSI `some avg10 <= 1.0` and `full avg10 <= 0.1`;
- stable CPU governor and no host sleep during the campaign;
- synchronized clock and sufficient disk space for all raw captures;
- Podman or Docker versions recorded in preflight.

## Frozen Runtime Contract

```text
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS/Leyden: disabled
runtime javaagent: absent
workload: 27 endpoints x 3 rounds
requests per arm: 81
```

Do not change warmup, workload, thresholds, JVM flags, artifacts, or pair
order in response to a noisy result.

## Execution Order

1. Run readiness and signed-manifest integrity validation.
2. Run host preflight.
3. Run two reversed B0-to-B0 same-artifact pairs.
4. Stop if median absolute B0 noise exceeds any frozen threshold.
5. Run two reversed V2-to-V2 same-artifact pairs only if B0 qualifies.
6. Stop if V2 noise exceeds any frozen threshold.
7. Run balanced product pairs in order B0/V2, V2/B0, B0/V2.
8. Run V2-C validation.
9. Run non-diagnostic V2-D attribution.
10. Publish the terminal verdict without deleting valid losing pairs.

## Frozen Noise Thresholds

```text
median |delta PSS| <= 1,024 KB
median |delta Private_Dirty| <= 1,024 KB
median |delta memory.current| <= 2,097,152 bytes
```

## Ledger Requirement

Every arm must emit one chronological Markdown ledger containing:

- every executable command;
- exit code, stdout, and stderr;
- auxiliary-service and target-service launch logs;
- warmup and all 81 workload responses;
- artifact, image, and runtime-policy proof;
- PSS, Private_Dirty, `memory.current`, smaps, NMT, heap, and histogram
  captures;
- pre/post host pressure;
- teardown.

The ledger, child ledgers, raw responses, and captures must be covered by an
integrity index. A summary without the command responses is not admissible.

## Admission Rule

If same-artifact noise fails on the dedicated Linux host, the campaign remains
environment blocked. Do not loosen thresholds and do not report a product
delta. If both controls qualify, the balanced product result may proceed to
V2-C and V2-D.
