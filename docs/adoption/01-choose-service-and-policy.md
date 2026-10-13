# 01 Choose Service And Policy

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

Choose a service with a reproducible build, a deterministic semantic workload,
and a runtime topology you can rebuild without private guesswork.

Freeze these choices before building:

```text
service and source revision
JDK vendor/build
launch mode
GC, heap, stack, and code-cache flags
CDS policy
allocator environment
support-service topology
workload and state-reset method
warmup, settle, and capture order
```

Record JVM identity as five separate fields. Do not use one `javaVersion`
label as a substitute for all of them:

| Identity | What To Freeze | Why It Is Separate |
| --- | --- | --- |
| Build JDK | vendor, version, and `java.home` used to compile JMOA and the service | Determines compiler and plugin execution behavior |
| Target bytecode | Maven/compiler `release` or source/target level | Determines which JVMs can load the produced classes |
| Runtime JDK | vendor, exact build, image digest, and JVM flags | Owns the measured memory behavior |
| CDS training JDK | exact runtime build and archive-producing command | A CDS archive is tied to its training/runtime identity |
| Analysis/Maven JDK | JDK running offline JMOA evidence goals | May be newer than the measured runtime but must never be presented as that runtime |

For each command ledger, record the relevant identity before the command:

```text
JAVA_HOME
java -version
mvn -version
compiler release
runtime image digest
CDS archive hash and training JDK, when applicable
```

Changing any runtime or CDS identity creates a new protocol. Changing only the
offline analysis JDK does not change captured runtime evidence, but it must
still be recorded so parser behavior is reproducible.

Freeze the actual deployment and any intentional packaging contrast. Historical
Doctor used artifact-specific AppCDS and earlier Patient used stock base CDS.
Stable 2.1 PetClinic compares R41F fat JAR with strict B0 exploded Boot, CDS off.
Provisional 2.15 Patient uses a separate Leyden cache per application artifact.
Both Patient arms use the same native trimming and allocator policy.

Do not copy Doctor's policy into another service because Doctor won. Packaging,
CDS, and page-touch behavior are service-specific.

## Gate

Proceed only when:

- source and runtime dependencies are available;
- the target and required support services can start from a fresh session;
- the workload reports exact request count, status, and errors;
- memory captures can run without JFR, class-load logging, or forced GC;
- every external command can be written to a scenario command ledger.

If any prerequisite is unavailable, classify the service as blocked. Do not
replace missing runtime proof with a portfolio summary.
