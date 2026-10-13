# 02 Build Clean B0

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

B0 is the product baseline: a clean build from the frozen source revision with
no JMOA plugin execution, runtime dependency, generated adapter, reducer
manifest, or runtime javaagent.

Build in a fresh worktree or clean clone. Record:

```text
source revision
complete build argv
effective POM and dependency tree
artifact SHA-256
application-entry and class-name fingerprints
resource fingerprint
dependency-name and dependency-content fingerprints
Boot loader fingerprint
image ID
```

Verify absence rather than assuming it:

```text
no jmoa.runtime classes
no JmoaPkgAdapters classes
no JMOA Maven execution
no reduced-library manifest
no javaagent
```

Changing plugin configuration in an already transformed workspace is not a
clean B0.

## Gate

B0 is admissible only when the no-JMOA proof passes and its source revision
matches V1/V2. If B0 comes from a different source or dependency universe,
classify `B0_SOURCE_MISMATCH` and stop before measurement.
