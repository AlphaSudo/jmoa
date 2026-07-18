# Compatibility Matrix

This matrix records tested boundaries, not every theoretically compatible
combination.

| Surface | Current boundary | Evidence |
| --- | --- | --- |
| Maven build runtime | Java 26 | Local qualification and GitHub Actions |
| Maven | 3.9.9 | Plugin API dependency and local qualification |
| Plugin classfile target | Java 22 (`maven.compiler.release=22`) | `jmoa-maven-plugin/pom.xml` |
| Runtime-library target | Java 17 (`maven.compiler.release=17`) | `jmoa-runtime-lib/pom.xml` |
| Public semantic runtime | Java 17 | Clean-clone PetClinic semantic smoke |
| Final private measurement runtime | Java 26 containers | Frozen Doctor and Patient protocols |
| Public Spring stack | Spring Boot 4.0.1 | Pinned PetClinic revision |
| Deployment shapes | Exploded Boot and Spring Boot fat JAR | PetClinic, Doctor, Patient |
| Runtime policies | no-CDS, stock JDK base CDS, application CDS | Service-specific final matrix |
| Container engine | Podman 5.7.1 locally | Build, smoke, and measurement automation |
| Local automation | Windows 11 and PowerShell 7 | Qualification environment |
| CI | `ubuntu-latest`, Temurin 26 | `.github/workflows/build.yml` |

## Important Distinctions

The plugin needs a Java 22-or-newer Maven process because its own classes target
22. Generated application/runtime classes retain explicit classfile-version
handling; the separate runtime support library targets Java 17. A Java 17
semantic smoke proves that one materialized public artifact can run there; it
does not imply every private Java 26 protocol has been backported.

Application CDS archive format and compatibility are tied to the exact runtime
and artifact. Archives are not portable across arbitrary JDK builds or artifact
hashes.

macOS, Docker Engine, Gradle, native-image, Windows containers, other Spring
Boot major versions, and JDKs below the declared targets are not qualified by
the current evidence.
