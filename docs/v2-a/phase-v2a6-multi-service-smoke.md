# Phase V2-A6 Multi-Service Smoke

Status: implementation smoke completed locally.

This is not a generated-class memory-win claim. Generated-class bytecode
mutation remains disabled. The smoke verifies that the V2-A scanners and
attribution/report layers can process the same service shapes used in the v1
portfolio work.

## PetClinic Customers-Service

Input shape:

```text
EXPLODED_BOOT_APP / JarLauncher / extracted layers
```

Local smoke result:

```text
roots scanned: 3
jars scanned: 162
classes scanned: 54,326
generated-like records: 12,152
generated runtime-loaded classes attributed from Phase 33 evidence: 8
```

Reports generated locally under `target/v2a-local-petclinic-smoke-full/`.

## Doctor-Service

Input shape:

```text
Corrected Spring Boot fat JAR
```

Local smoke result:

```text
classes scanned: 59,424
generated-like records: 14,469
families reported: 9
```

Reports generated locally under `target/v2a-local-doctor-smoke/`.

## Boundary

The smoke confirms source-tooling behavior across the two important deployment
shapes:

- exploded Boot,
- Spring Boot fat JAR with nested libraries.

It does not claim synthetic optimizer memory improvement. That requires a later
mutation-enabled prototype and paired service confirmation.
