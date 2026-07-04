# V2-E Implementation Status

Status: reducer prototype implemented; PetClinic runtime confirmation passed.

Implemented:

```text
jmoa:reduce-bytecode Maven goal
reducer disabled by default
report-only by default
unsafe strip flags fail fast
debug metadata savings estimator
LocalVariableTable / LocalVariableTypeTable reducer
reduced jar writer
reducer build reports
failure reports
unit tests for attribute preservation
PetClinic dependency artifact smoke
PetClinic reduced-image semantic smoke
```

PetClinic artifact smoke:

```text
input: Phase 33M full P2 exploded Boot dependency libs
jars processed: 162
classes scanned: 54,196
classes reduced: 12,697
classes skipped for BootstrapMethods: 1,480
original jar bytes: 92,466,274
reduced jar bytes: 87,070,377
removed jar bytes: 5,417,754
```

PetClinic service smoke:

```text
launch mode: EXPLODED_BOOT_APP
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
workload requests: 24
workload errors: 0
JVM linkage/class errors in sampled logs: false
semantic smoke: passed
```

PetClinic P2 vs P2+Reducer memory screen:

```text
comparison: full P2 vs full P2 + V2-E reducer
launch mode: EXPLODED_BOOT_APP
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
workload requests per variant: 81
workload errors: 0
artifact byte delta: -5,395,897
PSS delta: -17,880 KB
Private_Dirty delta: -18,068 KB
memory.current delta: -35,205,120 bytes
screen gate: passed
```

PetClinic 3-pair confirmation:

```text
comparison: full P2 vs full P2 + V2-E reducer
valid runs: 6/6
pairs: 3
paired wins: 2/3
workload errors: 0
V2-C verdict: CONFIRMED_WIN
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
```

Not claimed:

```text
startup win
fat-JAR win
CDS/AppCDS win
cross-service generalization
Doctor service-level confirmation
```

Next gate:

```text
Tag v0.7.0-v2e-runtime-confirmed after merge.
Then run V2-F against a second service or productize the release-low-footprint profile.
```
