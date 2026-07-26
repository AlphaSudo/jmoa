# PetClinic 2 GiB Support Calibration V2 Result

## Verdict

```text
SUPPORT_STACK_PRIVATE_MEMORY_UNSTABLE
```

The corrected authoritative campaign stopped before every target arm. This is
a support-stack admission result, not a baseline-to-V2 comparison and not a
JMOA regression.

The host had sufficient basic capacity throughout all three fresh support
calibrations:

- minimum `MemAvailable` remained above 1.07 GB;
- swap was disabled and unused;
- host and exact-container memory PSI remained zero;
- OOM and OOM-kill counters remained zero;
- config and discovery health remained UP;
- neither support container restarted;
- config and discovery used distinct individual libpod cgroups.

The failed dimension was final-window private-memory stability. All three
calibrations showed anonymous/private growth above at least one pre-registered
limit.

## Final 60-Second Matrix

| Calibration | PSS range KB | Private Dirty range KB | Anon range B | PSS slope B/s | Anon slope B/s | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | 8,200 | 8,200 | 8,409,088 | 126,587.097 | 126,694.146 | Failed range and slope gates |
| 2 | 6,156 | 6,160 | 6,217,728 | 73,595.789 | 74,731.029 | Failed range and slope gates |
| 3 | 3,152 | 3,152 | 3,231,744 | 62,498.897 | 63,819.498 | Slopes passed; ranges failed |

Frozen limits:

```text
PSS range             <= 2,048 KB
Private Dirty range   <= 2,048 KB
anonymous range       <= 2,097,152 bytes
positive PSS slope    <= 65,536 bytes/second
positive anon slope   <= 65,536 bytes/second
```

The exact-container file-memory range was zero in every final window. The
result is therefore classified `ANON_PRIVATE_GROWTH`, not
`FILE_CACHE_CHARGE`.

## Execution Integrity

- Corrected runner revision: `74c2df605c4f888ad46708df4f80fc225a44f463`.
- Source campaign SHA-256:
  `90CBB67E866F251164BA052C20EFC9F15DB0BC4A594D370DF32240836C5CA6C9`.
- Portable archive SHA-256:
  `C3C586FEEAB03720BCC05011E3DA67A4F7E3E7AD7479A58AEC606EAEADD82EEC`.
- Native Debian Gate A: 71 passed, 0 failed.
- Corrected dry-run readiness: passed.
- Support calibrations: 3 valid, 0 stable.
- Target arms launched: none.
- Retrieved evidence: 8 integrity indexes and 1,822 indexed files verified.

An earlier attempt at revision `a581cf6` is retained as invalid campaign
evidence. It failed before producing a support result because CRLF in a
PowerShell here-string reached Bash path arguments. Revision `74c2df6`
normalizes shell commands at the Bash boundary; a forced-CRLF Debian smoke and
the full corrected campaign proved the repair.

## Decision

The fixed 2 GiB VM is not admitted for this authoritative product campaign
under the frozen support-stability contract. No capacity arm, B0 control, V2
control, product pair, V2-C analysis, strict 4 MiB gate, or V2-D attribution
was run.

No threshold, support JVM flag, image, workload, artifact, or product gate was
changed. A future campaign needs a larger or cleaner host, or a separately
pre-registered support-stack change; this result must not be bypassed.

