# V2-K Target Selection

Selected target:

```text
Spring PetClinic visits-service
```

## Selection Criteria

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

## Why Not Doctor First?

Doctor remains the stronger private second-service candidate at artifact level,
but its runtime remains blocked by:

```text
private HMS compose/runtime stack
private database/config initialization
missing local Doctor runtime images
CDS archive mismatch for reduced artifact
```

V2-K should avoid waiting on private runtime recovery and use a public service
for the next portability signal.

## Alternatives

```text
Spring PetClinic vets-service
Spring PetClinic API gateway
another public Spring Boot service with simple Docker/Podman runtime
```

## Decision

Use `visits-service` first. If visits-service is blocked by runtime complexity,
fall back to `vets-service` before introducing a new repository.
