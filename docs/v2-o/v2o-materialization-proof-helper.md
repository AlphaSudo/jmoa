# V2-O Materialization Proof Helper

`scripts/prove-runtime-materialization.ps1` compares the host artifact and
dependency layer with the running container. It records app-jar SHA-256,
dependency-layer fingerprints, container/image identity, and CDS archive
mapping when CDS is requested.

The helper requires a source dependency directory and its corresponding
container directory. This lets it detect an original dependency layer shadowing
the reduced layer instead of relying on a container name alone.

For CDS, `PASSED` requires both archive SHA-256 equality and the archive name in
the Java process maps. For no-CDS, the caller must explicitly declare CDS,
AppCDS, Leyden, and javaagent state as disabled.

The report is evidence for V2-N and V2-C. It is not dynamic class-origin proof
and does not establish semantic correctness or performance.
