# PetClinic Hyper-V 2 GiB Terminal Outcome

## Verdict

The authoritative constrained-host attempt ended with:

```text
STOPPED_INSUFFICIENT_SUPPORT_STACK_HEADROOM
```

This is an environment qualification result, not a JMOA regression. The
campaign stopped before the capacity arm, B0 controls, V2 controls, or product
pairs, so this attempt contains no B0-to-V2 memory comparison.

## Frozen Identity

- Campaign runner revision: `4f26dd83dc4e9ef6d3d44202e8761bb217a67a9b`.
- Source campaign SHA-256:
  `90CBB67E866F251164BA052C20EFC9F15DB0BC4A594D370DF32240836C5CA6C9`.
- Portable package SHA-256:
  `AA4AE6F8A9C78B9B257A5CD15B093D8027D2C9C63CADB38DFEE4C5BC54CB0C24`.
- Archive SHA-256:
  `6CDE457364500644E64595F2957F24A23A53A841A0EBF1204C5FB6B2A67A0D5A`.
- Native Debian Gate A: 66 passed, 0 failed.
- Readiness dry run: passed.

The B0/V2 artifacts and all four OCI images remained frozen. Two failures found
before the final attempt were runner portability defects in the new Linux
calibration paths: ordered dictionaries were not exposed as PowerShell
properties, and a helper parameter collided with the automatic `$PID`
variable. Both failures occurred before a target arm, were fixed in committed
source, bound into Gate A, and shipped through fresh immutable packages.

## Admission Matrix

| Stage | Result | Evidence |
| --- | --- | --- |
| Host preflight | Passed | Fixed 2 GiB profile, four CPUs, native Podman, swap off |
| Host-idle calibration | Passed | Minimum `MemAvailable` 1,542,336,512 B; zero swap, PSI, and OOM events |
| Support-stack health | Passed | Config and discovery healthy for all ten samples; zero restarts |
| Support-stack headroom | Passed | Minimum `MemAvailable` 1,121,853,440 B, above the 734,003,200 B gate |
| Support-stack stability | **Failed** | `memory.current` drift 18,612,224 B; frozen limit 2,097,152 B |
| B0 capacity qualification | Not run | Blocked by support calibration |
| B0 same-artifact controls | Not run | Blocked by support calibration |
| V2 same-artifact controls | Not run | Blocked by support calibration |
| B0/V2 product pairs | Not run | Blocked by support calibration |
| V2-C / strict 4 MiB / V2-D | Not run | No admissible product evidence |

The support-stack samples had zero swap use, zero memory PSI `some/full avg10`,
zero `oom`/`oom_kill` events, HTTP 200 health, and zero restarts. The sole
disqualifier was support aggregate `memory.current` instability:

```text
minimum: 450,002,944 B
maximum: 468,615,168 B
drift:    18,612,224 B
limit:     2,097,152 B
```

## Command Evidence

The terminal campaign indexed five complete child ledgers containing 279
audited commands and HTTP captures. Their raw stdout, stderr, response bodies,
argument vectors, exit codes, and SHA-256 integrity records are retained in the
private evidence archive. The scenario summary records the terminal verdict
and points to the support calibration report.

## Claim Boundary

Allowed:

```text
The fixed 2 GiB Debian host passed idle and basic capacity/headroom checks, but
the frozen support-stack stability gate rejected it before any B0 or V2 target
arm. This does not change previously confirmed JMOA results.
```

Not allowed:

```text
V2 lost on the 2 GiB host.
The 2 GiB host produced a B0/V2 memory matrix.
The support drift threshold was relaxed to obtain product evidence.
```

The next authoritative product campaign requires a host on which the unchanged
support stack reproduces within the frozen 2 MiB `memory.current` drift limit.

