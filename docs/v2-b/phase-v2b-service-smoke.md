# Phase V2-B Service Smoke

Status: implementation smoke completed locally.

This validates the report-only profiler on the same deployment shapes used by
the v1 portfolio. It is not a bytecode reduction or memory-win claim.

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
classfile bytes scanned: 200,152,982
largest method code length: 57,198
```

## Doctor-Service

Input shape:

```text
Corrected Spring Boot fat JAR
```

Local smoke result:

```text
classes scanned: 59,424
classfile bytes scanned: 219,457,652
largest method code length: 57,198
```

## Boundary

The smoke confirms that V2-B handles:

- exploded Boot layers,
- dependency-layer jars,
- Spring Boot fat JARs,
- nested `BOOT-INF/lib` jars,
- V2-A generated-family classification in size reports.

No bytecode mutation was performed.
