# V2-K Doctor D2R CDS Training Result

Status:

```text
BLOCKED_WITH_ROOT_CAUSE
```

The preferred Doctor comparison remains:

```text
D2 + corrected D2 CDS
vs
D2R + freshly trained D2R CDS
```

The corrected D2 CDS archive exists, but the D2R raw-reduced CDS archive has not
been trained.

CDS retraining cannot proceed until the Doctor runtime stack is restored enough
to start the D2R candidate and perform a training shutdown.

Forbidden:

```text
reuse corrected D2 CDS with D2R
compare CDS baseline against no-CDS candidate
compare historical CDS result against new no-CDS diagnostic
```
