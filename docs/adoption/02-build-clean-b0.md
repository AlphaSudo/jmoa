# 02 Build Clean B0

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
