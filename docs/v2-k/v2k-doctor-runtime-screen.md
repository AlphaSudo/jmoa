# V2-K Doctor Runtime Screen

Status:

```text
EXECUTED_AND_PROMOTED
```

Doctor runtime screen ran after semantic smoke passed and fresh
variant-specific CDS archives were available.

Executed comparison:

```text
D2 + fresh current-runtime D2 CDS
vs
D2R + freshly trained D2R CDS
```

Forbidden comparisons that were avoided:

```text
historical CDS D2 vs new no-CDS D2R
D2 + old CDS vs D2R + old D2 CDS
D2 + old CDS vs D2R no-CDS
```

Promotion gate:

```text
artifact bytes lower
workload errors = 0
PSS not worse by > 1 MB
Private_Dirty not worse by > 1 MB
memory.current not worse by > 1 MB
```

Screen result:

```text
PSS delta: -1,689 KB
Private_Dirty delta: -1,608 KB
memory.current delta: -3,244,032 bytes
workload errors: 0
```

The screen promoted Doctor to 3-pair V2-C confirmation and V2-D attribution.
