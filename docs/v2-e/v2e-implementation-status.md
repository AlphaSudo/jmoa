# V2-E Implementation Status

Status: reducer prototype implemented; runtime screen passed; service
confirmation not claimed.

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

Not claimed:

```text
runtime memory win
startup win
PetClinic memory confirmation
Doctor service-level confirmation
```

Next gate:

```text
Run 3-pair P2 vs P2+Reducer V2-C confirmation and V2-D attribution.
Do not claim runtime memory benefit from the single-screen result.
```
