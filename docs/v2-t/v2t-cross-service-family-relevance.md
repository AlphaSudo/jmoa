# V2-T Cross-Service Family Relevance

V2-T does not compare raw V2-S family totals across services as though they were
runtime evidence. It compares evidence readiness instead.

| Service | Static inventory | SHA-matched lifecycle capture | Result |
| --- | --- | --- | --- |
| PetClinic customers | Present | No: workload-only capture has no artifact SHA and no startup/warmup | Partial diagnostic signal only |
| PetClinic visits | No matching V2-A inventory/capture pair | No | No generated-family conclusion |
| Doctor D2R | Present | No matching lifecycle capture or artifact SHA | Static-only signal |

The customers lambda runtime-only observation is useful for future capture
planning, but does not generalize to Doctor or visits and does not override the
V2-S safety registry.
