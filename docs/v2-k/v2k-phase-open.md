# V2-K Phase Open

V2-K starts after V2-J raw engine productization.

Goal:

```text
Resolve the Doctor runtime blocker and, in parallel or fallback, prepare a
public second runtime target for the V2-I/V2-J raw reducer.
```

V2-K does not begin with a runtime claim. It begins with Doctor runtime
inventory, CDS policy selection, image/materialization proof, and only then
semantic smoke or runtime screening.

The first executable gate is:

```text
scripts/doctor-runtime-inventory.ps1
```

The next executable pre-smoke gate is:

```text
scripts/doctor-runtime-unblock-gate.ps1
```

## Primary Target

```text
Doctor corrected D2
```

Why:

```text
Doctor already passed artifact-level reducer smoke.
Doctor is the stronger second-service validation target.
Doctor exercises the private fat-JAR/CDS runtime shape.
```

## Public Fallback Target

```text
Spring PetClinic visits-service
```

## Current Doctor Outcome

```text
BLOCKED_WITH_ROOT_CAUSE
```

Doctor can resume only after the missing private runtime pieces are restored.
Until then, the next executable V2-K lane is visits-service.

Reason:

```text
public source
same public PetClinic microservices repository
different service surface from customers-service
familiar Spring Boot deployment/materialization shape
public reproducibility is better than private Doctor runtime work
```

## Claim Boundary

V2-K is open. No second-service runtime claim exists yet.

Any V2-K runtime claim requires:

```text
runtime materialization proof
semantic smoke
artifact smoke
runtime screen
V2-C confirmation if the screen passes
V2-D attribution
```
