# V2-K Doctor Final Blocked Root Cause

Doctor recovery is closed for this sub-phase as:

```text
BLOCKED_WITH_ROOT_CAUSE
```

Root cause:

```text
The old Doctor runtime assets exist, but they are stale/private-runtime assets.
The new D2 + raw reducer target requires rebuilt/proven runtime images and a
fresh D2R CDS archive or an explicit no-CDS diagnostic policy.
Those runtime pieces are not currently ready.
```

This is not a reducer failure.

What was proven:

```text
old runtime scripts/compose assets exist
corrected D2 artifact exists
D2 + raw reducer artifact exists
corrected D2 CDS archive exists
```

What remains blocked:

```text
D2R CDS training
runtime image rebuild
private runtime inputs
runtime materialization proof
semantic smoke
runtime screen
V2-C confirmation
V2-D attribution
```

Next allowed move:

```text
Start the public visits-service fallback lane,
or resume Doctor only after the missing private runtime pieces are restored.
```

No Doctor runtime claim is made.
