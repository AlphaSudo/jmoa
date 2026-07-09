# V2-K Target Selection

Primary target:

```text
Doctor corrected D2
```

Fallback / public target:

```text
Spring PetClinic visits-service
```

## Doctor Selection Criteria

| Criterion | Result |
| --- | --- |
| Already has artifact smoke | pass |
| Raw reducer artifact smoke available | pass |
| Private second-service credibility | high |
| Fat-JAR/CDS policy coverage | high |
| Runtime currently runnable | blocked |
| Private stack dependency | present |

Doctor remains the preferred second runtime target, but it must be unblocked
honestly.

## Public Fallback Selection Criteria

| Criterion | Result |
| --- | --- |
| Public source | pass |
| Public reproducibility | pass |
| Spring Boot service | pass |
| Different from customers-service | pass |
| Docker/Podman runtime likely manageable | candidate |
| Representative workload possible | candidate |
| Existing project familiarity | pass |
| No private HMS dependency | pass |

## Why Keep Visits-Service?

Visits-service keeps the public reproducibility lane moving if Doctor remains
blocked by the private runtime stack.

## Doctor Runtime Blockers

Doctor runtime currently remains blocked by:

```text
private HMS compose/runtime stack
private database/config initialization
missing local Doctor runtime images
CDS archive mismatch for reduced artifact
```

## Alternatives

```text
Spring PetClinic vets-service
Spring PetClinic API gateway
another public Spring Boot service with simple Docker/Podman runtime
```

## Decision

V2-K should work Doctor first. If Doctor remains blocked after the runtime
inventory/rebuild attempt, start `visits-service` as the public second-runtime
track. If visits-service is blocked by runtime complexity, fall back to
`vets-service` before introducing a new repository.
