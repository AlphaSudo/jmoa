# V2-K Doctor Runtime Screen Result

Status:

```text
PASSED_PROMOTED_TO_CONFIRMATION
```

The first Doctor D2 vs D2R runtime screen passed after the local Doctor runtime
stack was recovered and fresh variant-specific CDS archives were used.

Comparison:

```text
D2 + fresh current-runtime D2 CDS
vs
D2R + freshly trained D2R CDS
```

Screen result:

```text
PSS delta: -1,689 KB
Private_Dirty delta: -1,608 KB
memory.current delta: -3,244,032 bytes
workload errors: 0
verdict: SCREEN_PASSED_PROMOTE_TO_3PAIR
```

This single screen is superseded by the 3-pair V2-C confirmation:

```text
V2-C verdict: CONFIRMED_WIN
paired wins: 3/3
median PSS delta: -5,156 KB
median Private_Dirty delta: -5,212 KB
median memory.current delta: -6,975,488 bytes
```
