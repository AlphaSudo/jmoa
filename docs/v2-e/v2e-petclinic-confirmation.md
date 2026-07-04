# V2-E PetClinic Confirmation

Status: confirmed incremental runtime win.

This confirmation compares:

```text
full P2
vs
full P2 + V2-E LocalVariableTable / LocalVariableTypeTable reducer
```

It does not compare against the original baseline service. The question here is
whether V2-E adds stable incremental value on top of the already-confirmed full
P2 artifact.

## Protocol

```text
service: Spring PetClinic customers-service
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS/Leyden: disabled
runtime javaagent: absent
workload: corrected 27 endpoints x 3 rounds
pairs: 3
```

Class-load logging, JFR, and explicit `GC.run` were not enabled during the memory
pairs.

## Artifact Gate

```text
full P2 dependency jars: 92,466,274 bytes
full P2 + reducer dependency jars: 87,070,377 bytes
artifact delta: -5,395,897 bytes
jar count stable: true
sample materialization hashes matched: true
```

## Evidence Gate

```text
runs: 6
valid runs: 6
invalid runs: 0
workload errors: 0
V2-C verdict: CONFIRMED_WIN
```

## Pair Results

| Pair | PSS KB | Private_Dirty KB | memory.current bytes | Heap PSS KB | anonymous_rw PSS KB | Pass |
| ---: | ---: | ---: | ---: | ---: | ---: | --- |
| 1 | -1,624 | -1,636 | -18,149,376 | +1,572 | -2,456 | true |
| 2 | -12,093 | -12,084 | -12,255,232 | -10,468 | -1,248 | true |
| 3 | +546 | +768 | +831,488 | +2,992 | -2,708 | false |

## Median Result

```text
paired wins: 2/3
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
median heap PSS delta: +1,572 KB
median anonymous_rw PSS delta: -2,456 KB
```

## Claim Boundary

V2-E is confirmed as an incremental runtime reducer for this PetClinic
`EXPLODED_BOOT_APP` no-CDS protocol.

Do not generalize this to:

```text
startup win
all Spring Boot services
fat-JAR deployment
CDS/AppCDS deployment
all debug metadata stripping
LineNumberTable or StackMapTable stripping
```
