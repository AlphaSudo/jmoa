# V2-J Doctor Runtime Unblock Plan

Doctor remains blocked for runtime confirmation.

The V2-J raw artifact smoke is useful, but it is not enough for a runtime claim.
Before Doctor runtime measurement, the project needs a clean runtime policy.

## Current Blockers

```text
private HMS compose/runtime stack dependency
private database/config initialization dependency
Doctor runtime images not available locally
existing corrected D2 CDS archive was trained for the non-reduced artifact
raw-reduced D2 artifact needs a fresh policy-specific runtime proof
```

## Allowed Paths

### Option A: retrain CDS for D2 + raw reducer

Use this if the goal is comparable corrected fat-JAR/CDS evidence.

Requirements:

```text
rebuild Doctor runtime image stack
materialize D2 + raw reducer fat JAR
train fresh CDS archive against the reduced artifact
prove runtime class origins
run semantic smoke
then run V2-C/V2-D-gated memory screen
```

### Option B: no-CDS diagnostic

Use this only as an explicit diagnostic path.

Requirements:

```text
state that it is not comparable to historical Doctor CDS evidence
disable CDS/AppCDS
prove runtime class origins
run semantic smoke
then run screen only
```

### Option C: keep Doctor runtime blocked

Use this if the private image/config stack is unavailable.

## Explicitly Forbidden

```text
do not reuse the old D2 CDS archive with the raw-reduced artifact
do not compare reduced no-CDS runs to historical CDS runs as equivalent
do not claim Doctor runtime memory savings from artifact smoke
```

Recommended next action:

```text
If Doctor remains private-stack blocked, select a public second runtime target for V2-K.
```
