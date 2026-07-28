# 06 Run Runtime Policy

Use the last accepted service runtime. Do not invent one during confirmation.

## Accepted service policies

| Service | Deployment | Policy |
|---|---|---|
| Doctor | corrected Spring Boot fat JAR | application CDS, one trained archive per artifact |
| Patient | Spring Boot fat JAR | stock JDK base CDS, `MALLOC_ARENA_MAX=1` |
| PetClinic customers | exploded Boot `JarLauncher` | no CDS, `-Xshare:off`, Serial GC, `MALLOC_ARENA_MAX=1` |

Every observation starts a fresh full support stack and exactly one target JVM. After capture, tear down the target, support services, and isolated network.

Application CDS archives are artifact-specific. Never reuse a V1 archive for V2.
