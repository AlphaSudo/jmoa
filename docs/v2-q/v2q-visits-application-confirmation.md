# V2-Q Visits Application Confirmation

After the diagnostic rerun passed, V2-Q ran a fresh 3-pair confirmation for the
same comparison:

```text
dependency raw only
vs
dependency raw + admitted application raw
```

The confirmation used the public PetClinic visits-service exploded-Boot/no-CDS
protocol with `MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden, and no runtime
javaagent.

## Result

| Pair | PSS delta | Private_Dirty delta | memory.current delta | Gate |
| ---: | ---: | ---: | ---: | --- |
| 1 | `-7,096 KB` | `-6,692 KB` | `-6,893,568 bytes` | pass |
| 2 | `+5,732 KB` | `+5,760 KB` | `+5,922,816 bytes` | fail |
| 3 | `+6,460 KB` | `+6,212 KB` | `+6,340,608 bytes` | fail |

Summary:

```text
pairs: 3
paired wins: 1/3
median PSS delta: +5,732 KB
median Private_Dirty delta: +5,760 KB
median memory.current delta: +5,922,816 bytes
workload errors: 0
linkage errors: 0
```

The confirmation failed. V2-Q therefore makes no runtime-memory claim and does
not run V2-C/V2-D as a claim pipeline.
