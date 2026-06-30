# Deployment Materialization

JMOA output must be placed into the deployment shape the service actually runs.

The portfolio work showed that this is not cosmetic. The same optimizer output
can behave differently when launched as a corrected fat JAR, an expanded
classpath, or an exploded Spring Boot application.

## Supported Shapes

### Expanded Classpath

Optimized jars are placed before original jars on a generated runtime classpath.
The verifier must ensure original jars do not shadow optimized jars.

### Spring Boot Fat JAR

Optimized dependency jars must replace original entries under `BOOT-INF/lib`.
The materializer must preserve Spring Boot launcher compatibility, classpath
indexes, and layer metadata where applicable.

### Exploded Boot App

The application is extracted into layers such as dependencies,
snapshot-dependencies, spring-boot-loader, and application. Optimized dependency
jars must be placed into the dependency layer used by `JarLauncher`.

## Product Lesson

Doctor-service required corrected fat-JAR substitution before its CDS result was
valid. PetClinic only confirmed the no-CDS win after the optimizer was
materialized into the project's real exploded Boot deployment shape.

That is why JMOA's product boundary is not "rewrite classes." It is:

```text
candidate + bytecode + packaging + classpath + runtime policy + verification
```
