# V2 Clean-Clone Qualification

Status: `PASSED`

The full isolated quickstart was run from remote commit `973257e`, cloned into
a fresh directory and built with an empty isolated Maven repository. The run
took 1,104.5 seconds and passed:

- reactor tests;
- six-family schema compatibility;
- publication safety;
- RC bundle build and local installation;
- pinned PetClinic full-P2 optimization;
- raw LVT/LVTT reduction;
- exploded-Boot materialization with runtime-library hash proof;
- Java 17 health and endpoint semantic smoke.

Full P2 scanned 11,882 classes and 7,331 lambda sites, producing 24 optimized
dependency JARs. The reducer processed 8,619 classes across those JARs and
removed 776,286 bytes. Materialization replaced all 24 JARs and copied the
matching `jmoa-runtime-lib` into `BOOT-INF/lib`.

The final RC source/tag is merge commit `52c261c`. Changes after `973257e` were
limited to release documentation, RC qualification records, and release-package
cleanup isolation; no optimizer, protocol, artifact, or quickstart behavior was
changed. The final bundle was rebuilt and checksummed from `52c261c` before
tagging `v2.0.0-rc1`.

GitHub Actions run `29297868936` separately passed publication safety and Maven
tests on `ubuntu-latest` with Temurin 26 for the final merge commit. The
clean-clone runtime and independent CI build jointly close the RC environment
qualification gate.

This record is not a new memory claim. See the final memory matrix for accepted
three-pair results.
