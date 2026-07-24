# PetClinic Dedicated Linux Campaign Contract

Status: **FROZEN BEFORE LINUX EXECUTION**

The Windows-to-WSL-to-Podman result is terminal:
`ENVIRONMENT_VARIANCE_TOO_HIGH`. No third Windows campaign is permitted.

## Objective

Transport the exact signed PetClinic product to a dedicated Debian 13 Hyper-V
VM and determine whether B0 and V2 reproduce within the frozen noise limits.
The first Linux campaign may not rebuild artifacts or images.

## Environment

```text
guest: Debian GNU/Linux 13 (trixie)
kernel: 6.12.74+deb13+1-amd64
virtualization: Microsoft Hyper-V
architecture: x86_64
container runtime: native Linux Podman
cgroup: v2
minimum fixed RAM: 8 GiB
fixed vCPU: 4
dynamic memory: disabled
swap used: 0
```

The VM is admissible only if its measured conditions satisfy the gates. Hyper-V
is not assumed quiet merely because it removes WSL and Podman machine layers.

## Immutable Product

```text
source campaign:
90CBB67E866F251164BA052C20EFC9F15DB0BC4A594D370DF32240836C5CA6C9

B0 artifact:
88D4BC9F000A22041F8FD521A0442B85F9FC49966948FA11F642CBBABE0D6AE7

V2 artifact:
338F5D44431E66B3EEC9B2CFAD6D9769D70D08E3C351B6FA221AE883D8E5A34B
```

The package exports the exact B0, V2, config, and discovery images as OCI
archives. Import must verify archive SHA-256, every package file, loaded image
config IDs, B0 cleanliness, the V2 runtime library, all 24 replacement hashes,
application/loader fingerprints, and unchanged dependencies.

## Frozen Runtime

```text
launch: EXPLODED_BOOT_APP / JarLauncher
policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS/Leyden/javaagent: absent
warmup: 20 seconds
workload: 27 endpoints x 3 rounds
settle: 5 seconds
```

## Gates

1. Gate A fixtures pass with the committed script hashes.
2. Imported OCI and artifact identities pass.
3. Linux dry-run reports ready.
4. Three support-stack-only calibration samples have at most 2 MiB aggregate
   `memory.current` drift, zero swap, and no sustained memory PSI.
5. Two reversed B0 pairs satisfy:
   - median absolute PSS <= 1,024 KB;
   - median absolute Private_Dirty <= 1,024 KB;
   - median absolute `memory.current` <= 2,097,152 bytes.
6. Only after B0 passes, two reversed V2 controls satisfy the same limits.
7. Only after both pass, run B0/V2, V2/B0, B0/V2.
8. V2-C requires 6/6 valid runs, at least 2/3 wins, zero errors, and
   `CONFIRMED_WIN`.
9. A substantial product win additionally requires median PSS <= -4,096 KB,
   Private_Dirty <= -1,024 KB, and `memory.current` <= -1,048,576 bytes.
10. V2-D runs with `diagnosticOnly=false` and `requireV2CValid=true`.

No threshold, workload, warmup, settle, JVM, artifact, or optimizer change is
allowed after execution begins.

## Terminal States

```text
TRUSTED_PRODUCT_WIN
CONFIRMED_PRODUCT_WIN_BELOW_4MIB
PRODUCT_EFFECT_NOT_CONFIRMED
ENVIRONMENT_VARIANCE_TOO_HIGH
STOPPED_LINUX_HOST_NOT_QUIET
STOPPED_PROTOCOL_VARIANCE
V2_ARTIFACT_RUNTIME_VARIANCE
STOPPED_IMAGE_TRANSPORT_MISMATCH
```

Every command and response must be present in integrity-indexed Markdown and
NDJSON ledgers. A summary without its command ledger is not evidence.
