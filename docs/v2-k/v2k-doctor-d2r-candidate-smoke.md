# V2-K Doctor D2R Candidate Smoke

Status:

```text
PASSED
```

This smoke validates the raw-reduced Doctor D2R candidate runtime used for the
V2-K runtime comparison.

Runtime scope:

```text
service: Doctor corrected D2 plus raw reducer
launch mode: SPRING_BOOT_FAT_JAR
runtime policy: CDS with a fresh D2R archive trained for the reduced artifact
javaagent: absent
class-load logging: disabled during memory captures
JFR: disabled
NMT: summary
```

Candidate smoke summary across the three confirmation candidate runs:

| Metric | Median |
| --- | ---: |
| Post-workload PSS | `321,979 KB` |
| Post-workload Private_Dirty | `290,448 KB` |
| Post-workload memory.current | `426,975,232 bytes` |
| Startup timing | `34.1 s` |

Workload:

```text
runs: 3
requests per run: 36
workload errors: 0
health: UP
secured Doctor endpoint: exercised
```

Fresh D2R CDS archive:

```text
bytes: 126,636,032
sha256: 64A4331695D092148A105ADAA47FEEA0CA46CB0CC561C3289F0413D1A67B6ACC
mapped at runtime: yes
```

Claim boundary:

```text
This proves the D2R candidate starts and serves the smoke workload with a fresh
candidate-specific CDS archive. The runtime memory claim is made only by the
3-pair V2-C confirmation.
```
