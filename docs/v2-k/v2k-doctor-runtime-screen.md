# V2-K Doctor Runtime Screen

Status:

```text
NOT_ATTEMPTED
```

Doctor runtime screen is blocked until semantic smoke passes.

Valid comparisons:

```text
D2 + corrected D2 CDS
vs
D2R + freshly trained D2R CDS
```

or, if CDS retraining remains blocked and the diagnostic path is explicitly
selected:

```text
D2 no-CDS
vs
D2R no-CDS
```

Forbidden comparisons:

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

No runtime screen means no V2-C confirmation and no V2-D attribution.
