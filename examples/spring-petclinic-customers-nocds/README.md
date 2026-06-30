# Spring PetClinic Customers No-CDS Example

This example is the public reproduction scaffold for the confirmed PetClinic
case study.

The target service is:

```text
spring-petclinic/spring-petclinic-microservices
spring-petclinic-customers-service
```

## Claim Boundary

The accepted portfolio claim used:

- no CDS,
- no AppCDS,
- no Leyden,
- no runtime javaagent,
- `MALLOC_ARENA_MAX=1`,
- exploded Boot / `JarLauncher`,
- dynamic runtime-origin verification,
- corrected workload,
- 3 paired baseline/optimized runs.

## Scripts

The scripts are intentionally numbered:

1. `01-clone-petclinic.ps1`
2. `02-build-baseline.ps1`
3. `03-build-jmoa-p2.ps1`
4. `04-materialize-exploded-boot.ps1`
5. `05-run-baseline.ps1`
6. `06-run-optimized.ps1`
7. `07-measure-paired.ps1`
8. `08-analyze-results.ps1`

They are a public-safe scaffold. Before using them for a final claim, run them
from a fresh directory and verify the exact Spring PetClinic commit and JMOA
plugin version.

## Expected Outputs

Measurement output should follow
[schemas/paired-result.schema.json](schemas/paired-result.schema.json).

The final report should state:

- artifact hashes,
- launch mode,
- runtime policy,
- workload errors,
- PSS,
- Private_Dirty,
- `memory.current`,
- heap PSS,
- loaded classes,
- dynamic runtime-origin proof status.
