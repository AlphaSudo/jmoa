# V2-L Visits Runtime Confirmation

Status:

```text
CONFIRMED_WIN
```

V2-L confirms the productized raw LVT/LVTT reducer on a second public runtime
target.

Comparison:

```text
public Spring PetClinic visits-service baseline
vs
the same baseline + raw LVT/LVTT reducer
```

Runtime scope:

```text
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
Java 17 container runtime
embedded HSQLDB
18-request visits workload per run
```

V2-C result:

```text
valid runs: 6/6
invalid runs: 0
paired wins: 3/3
verdict: CONFIRMED_WIN
median PSS delta: -2,012 KB
median Private_Dirty delta: -1,680 KB
median memory.current delta: -1,712,128 bytes
```

Pair table:

| Pair | PSS KB | Private_Dirty KB | memory.current bytes | Workload errors |
| ---: | ---: | ---: | ---: | ---: |
| 1 | `-2,012` | `-1,680` | `-1,712,128` | `0` |
| 2 | `-3,172` | `-3,676` | `-3,784,704` | `0` |
| 3 | `-1,372` | `-1,248` | `-1,298,432` | `0` |

Artifact result:

```text
dependency-layer compressed-byte delta: -3,515,600
classes reduced and byte-audited: 29,701
failed audits: 0
```

Startup deltas were negative in all three pairs, but startup was not controlled
as a claim metric and no startup claim is made.

This is a baseline-plus-reducer confirmation. A visits full-P2 profile was not
available, so the result is not described as incremental value over full P2.
