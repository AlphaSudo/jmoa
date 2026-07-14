# V2 Clean-Clone Qualification

Status: `PASSED`

Remote commit `973257e` was cloned into a fresh directory and built with an
empty isolated Maven repository. The run took 1,104.5 seconds and passed:

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

GitHub Actions run `29297537251` separately passed publication safety and Maven
tests on `ubuntu-latest` with Temurin 26. The clean-clone runtime and independent
CI build jointly close the RC environment qualification gate.

This record is not a new memory claim. See the final memory matrix for accepted
three-pair results.
