# V2 Public Quickstart Qualification

Status: `LOCAL_AND_CLEAN_CLONE_GOLDEN_PATH_PASSED`

The public PetClinic customers path completed against pinned revision
`305a1f13e4f961001d4e6cb50a9db51dc3fc5967` using JMOA `2.0.0-rc1`.

## Results

- Full P2 scanned 11,882 classes and 7,331 lambda sites.
- Full P2 produced 24 optimized dependency JARs.
- The raw reducer processed 24 JARs and 8,619 classes, removing 776,286 bytes.
- Exploded-Boot materialization replaced all 24 dependency JARs.
- `jmoa-runtime-lib` was added to `BOOT-INF/lib` with matching source/target SHA-256.
- Java 17 startup reached health `UP`; three endpoint checks returned with zero errors.
- No `VerifyError`, `ClassFormatError`, or linkage error was observed.

The first smoke attempt exposed a missing runtime-library materialization step
and failed with `NoClassDefFoundError: jmoa/runtime/JmoaFactory`. The
materializer now treats the runtime library as a required, hash-audited part of
the deployment artifact. The corrected run passed.

The same path then passed from remote commit `973257e` with an empty isolated
Maven repository. That run rebuilt and installed the RC bundle before invoking
full P2, so it did not rely on a JMOA artifact from the development repository.

This is build, packaging, and semantic qualification, not a new memory result.
The accepted memory claims remain those in the final three-pair matrix.
