# JMOA V2 Three-Service Acceptance Contract

Status: `FROZEN`

This contract defines the final V2 acceptance gate. It compares the final V1 artifact with the final V2 artifact for each service. Historical baseline-to-candidate results are context only and cannot satisfy this gate.

## Services

The contract has exactly three services:

1. Public Spring PetClinic customers-service
2. Private Doctor service
3. Private Patient service

## Per-service gate

Every service must provide all of the following:

- `validRuns = 6/6`
- `pairedWins >= 2/3`
- median PSS delta `<= -4096 KB`
- median Private_Dirty delta `<= -1024 KB`
- median `memory.current` delta `<= -1048576 bytes`
- zero workload errors
- zero semantic, verifier, linkage, or class-format errors
- V2-C verdict `CONFIRMED_WIN`
- V2-D attribution present
- frozen V1 and V2 artifact identities and runtime policy

The primary comparison is always:

```text
final V1 -> final V2
```

The following are explicitly excluded from the final matrix:

- baseline -> V2 comparisons
- historical medians copied from another phase
- a different launch mode or CDS policy than the frozen V1 run
- a substituted service
- a single screen presented as confirmation
- a report without run-level evidence

## Audited results

### PetClinic

The latest audited final V1-to-V2 record passes the contract:

```text
valid runs:              6/6
paired wins:             2/3
median PSS delta:        -6012 KB
median Private_Dirty:    -5708 KB
median memory.current:   -8081408 bytes
V2-C:                    CONFIRMED_WIN
V2-D:                    present
```

Runtime scope: exploded Spring Boot application, no CDS/AppCDS/Leyden, `MALLOC_ARENA_MAX=1`, no runtime javaagent, corrected workload.

### Doctor

The current V2-K record passes the contract:

```text
valid runs:              6/6
paired wins:             3/3
median PSS delta:        -5156 KB
median Private_Dirty:    -5212 KB
median memory.current:   -6975488 bytes
V2-C:                    CONFIRMED_WIN
V2-D:                    present
```

Runtime scope: corrected fat JAR, variant-specific CDS archives, no runtime javaagent, and the documented Doctor workload.

### Patient

Patient remains pending until a final V1-to-V2 six-run comparison is recovered or regenerated. Phase 31 C2 versus baseline evidence is historical context and is not accepted as this contract's Patient result.

## Release rule

The aggregate result is `READY_FOR_V2_FINAL` only when all three service verdicts pass. Otherwise the aggregate result is `BLOCKED_PATIENT_EVIDENCE` or another explicit fail-closed status. No stable three-service claim may be published while Patient is pending.

## Evidence boundary

Raw private evidence must remain outside the public repository. The repository may contain sanitized verdicts, hashes, schemas, and reproducibility notes, but never private credentials, local paths, private configuration, database dumps, or raw private logs.
