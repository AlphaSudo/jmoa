# JMOA V2 Three-Service Acceptance Contract

Status: `FROZEN`

This contract defines the final V2 acceptance gate. It compares the final V1 artifact with the final V2 artifact for each service under the service's confirmed runtime policy. Historical baseline-to-candidate results are context only and cannot satisfy this gate.

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
- a different launch mode or runtime policy than the frozen V1 run for that service
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

The corrected Patient V1-to-V2 record passes under `NO_CDS_LOW_DIRTY`:

```text
valid runs:              6/6
paired wins:             2/3
median PSS delta:        -8903 KB
median Private_Dirty:    -8636 KB
median memory.current:   -9707520 bytes
V2-C:                    CONFIRMED_WIN
V2-D:                    present
```

Runtime scope: corrected Spring Boot fat JAR, CDS/AppCDS/Leyden disabled,
`MALLOC_ARENA_MAX=1`, no runtime javaagent, and the final 600-request workload.
The separate corrected CDS record remains blocked (`1/3` paired wins, median
PSS `+668 KB`) and is preserved as the authoritative CDS-policy failure. The
Patient policy is therefore service-specific: no-CDS is admissible; CDS is not.

## Release rule

The aggregate result is `READY_FOR_V2_FINAL` when all three services pass under
their individually confirmed policies. The current matrix is:

```text
PetClinic: no-CDS, PASS
Doctor:    CDS,    PASS
Patient:   no-CDS, PASS; CDS BLOCKED
aggregate: READY_FOR_V2_FINAL
```

This is not a universal CDS claim. Runtime-policy selection is part of the
acceptance result, and evidence cannot be transferred between policies.

## Evidence boundary

Raw private evidence must remain outside the public repository. The repository may contain sanitized verdicts, hashes, schemas, and reproducibility notes, but never private credentials, local paths, private configuration, database dumps, or raw private logs.
