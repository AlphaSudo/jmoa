# Spring PetClinic Customers No-CDS Example

This is the executable public golden path for building and smoke-testing the
JMOA V2 PetClinic customers artifact. It is pinned to PetClinic revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967`.

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

Use `00-quickstart.ps1` for the qualified build path. It performs:

1. pinned PetClinic clone;
2. baseline compilation;
3. full-P2 dependency optimization with the frozen profile, admission keys,
   SAM allowlist, and runtime library;
4. opt-in raw LVT/LVTT reduction;
5. exploded-Boot materialization with hash proof;
6. Java 17 semantic smoke against config and discovery services.

The `05` through `08` scripts remain measurement scaffolding. They are not
invoked by the build-only quickstart and do not replace the accepted V2-C
paired confirmation protocol.

## Quick Start

Install the plugin and runtime POM/JAR pairs from the `2.0.0-rc2` GitHub
Release bundle, then run:

```powershell
.\scripts\00-quickstart.ps1 `
  -ProfilePath <petclinic-customers-profile.json> `
  -AdmissionPath <petclinic-customers-admission.txt> `
  -SafeSamsPath <jmoa-additional-safe-sams.txt> `
  -RuntimeJar <jmoa-runtime-lib-2.0.0-rc2.jar>
```

Podman must be available for semantic smoke. Add `-SkipSemanticSmoke` only for
artifact-building diagnostics; that mode is not a runtime qualification.

The quickstart is public-safe and fails if full-P2 inputs are absent, no
optimized dependency JARs are produced, the runtime library is not
materialized, hashes disagree, or semantic smoke fails.

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

The quickstart proves build and semantic viability. The published memory
numbers come from the separate frozen paired-confirmation evidence and retain
the service/protocol-specific claim boundary documented in `docs/v2-final`.
