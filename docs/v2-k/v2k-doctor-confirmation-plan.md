# V2-K Doctor Confirmation Plan

Doctor 3-pair confirmation is not allowed until the single runtime screen
passes.

If the CDS path is used, the confirmation comparison must be:

```text
D2 + corrected D2 CDS
vs
D2R + freshly trained D2R CDS
```

Pair order:

```text
D2-1  -> D2R-1
D2-2  -> D2R-2
D2-3  -> D2R-3
```

Runtime claim gate:

```text
valid runs = 6/6
paired wins >= 2/3
median PSS <= -1 MB
median Private_Dirty <= -1 MB
median memory.current <= -1 MB
workload errors = 0
V2-C verdict = CONFIRMED_WIN
V2-D attribution present
```

Current status:

```text
NOT_ALLOWED
SEMANTIC_SMOKE_NOT_RUN
RUNTIME_SCREEN_NOT_RUN
```
