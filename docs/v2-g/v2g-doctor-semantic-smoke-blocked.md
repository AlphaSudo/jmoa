# V2-G Doctor Semantic Smoke Blocked

Status:

```text
BLOCKED
```

Reason:

```text
The corrected Doctor runtime depends on a private HMS compose stack.
The local private config and database inputs are present, but they are not
publishable and are not part of this source repo.
```

Local runtime checks:

```text
Podman available: true
private config inputs present: true
corrected D2 CDS archive present: true
Doctor D2-fixed image present: false
Doctor baseline image present: false
```

Additional constraint:

```text
The corrected D2 CDS archive was trained for the non-reduced D2 artifact.
It should not be reused as runtime-confirmation proof for the reduced artifact
without explicit retraining or a documented no-CDS policy decision.
```

Because semantic smoke is blocked, V2-G does not run a runtime memory screen or
3-pair confirmation for Doctor in this milestone.

