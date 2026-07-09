# V2-K Doctor Runtime Confirmation

Status:

```text
CONFIRMED_WIN
```

V2-K confirms the Doctor raw-reduced D2R artifact as an incremental runtime win
over corrected D2 under the recovered private Doctor runtime stack.

Comparison:

```text
D2 + fresh current-runtime D2 CDS
vs
D2R + fresh D2R CDS
```

Runtime scope:

```text
launch mode: SPRING_BOOT_FAT_JAR
runtime policy: CDS
CDS archives: retrained per variant for the current Java runtime
javaagent: absent
class-load logging during memory pairs: disabled
JFR: disabled
NMT: summary
workload: 36-request Doctor smoke per run
```

V2-C result:

```text
valid runs: 6/6
paired wins: 3/3
verdict: CONFIRMED_WIN
median PSS delta: -5,156 KB
median Private_Dirty delta: -5,212 KB
median memory.current delta: -6,975,488 bytes
```

Pair table:

| Pair | PSS KB | Private_Dirty KB | memory.current bytes | Workload errors |
| ---: | ---: | ---: | ---: | ---: |
| 1 | `-5,156` | `-5,212` | `-6,975,488` | `0` |
| 2 | `-10,511` | `-10,532` | `-12,423,168` | `0` |
| 3 | `-3,520` | `-3,872` | `-5,627,904` | `0` |

Observed startup deltas were negative in all three pairs, but V2-K does not make
a startup claim. Startup was captured as supporting context only.

Claim boundary:

```text
Confirmed for the private Doctor corrected D2 vs raw-reduced D2R comparison
under Spring Boot fat JAR with variant-specific CDS archives.

Not claimed:
- public reproducibility
- all Doctor deployments
- all fat-JAR services
- all CDS/AppCDS modes
- startup improvement
- generated-class mutation
```
