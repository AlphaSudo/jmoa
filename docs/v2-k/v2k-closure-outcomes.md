# V2-K Closure Outcomes

V2-K can close in one of these states:

| Outcome | Meaning | Tag guidance |
| --- | --- | --- |
| `CLOSED_CONFIRMED_DOCTOR` | Doctor passes smoke, screen, V2-C confirmation, and V2-D attribution | `v0.9.0-v2k-doctor-runtime-confirmed` |
| `CLOSED_CONFIRMED_PUBLIC_SECOND_RUNTIME` | Doctor remains blocked but visits-service confirms publicly | `v0.9.0-v2k-public-second-runtime-confirmed` |
| `SCREEN_FAILED` | Doctor smoke passes but runtime screen fails | no runtime-generalization tag |
| `BLOCKED` | Doctor remains blocked and visits-service does not run | no runtime-generalization tag |
| `ARTIFACT_ONLY` | Doctor or visits only reach artifact/smoke evidence | optional portability-smoke tag only |

Current state:

```text
BLOCKED_WITH_ROOT_CAUSE
```

Reason:

```text
Doctor runtime stack recovery was attempted and remains blocked by stale/private
runtime assets, missing private inputs, missing images, missing network, and
missing D2R CDS training.
Visits-service fallback has not been started yet.
```
