# 04 Build V2

**Release context (2026-10-13):** stable 2.1.0 ships the Maven tooling and
accepted packaging-inclusive PetClinic result. Provisional 2.15 adds Patient
Java 26/Leyden benchmark evidence, with failed original calibration admitted by
exception and unqualified or failed performance bounds; no new Maven binaries.
See [current release boundaries](../releases/current-state.md).

The numbered B0/V1/V2 procedures below describe the historical three-artifact
evaluation workflow. Their example outcomes and session counts are not the
2.1 or 2.15 release protocol.

V2 starts from the accepted V1 dependency universe and applies the productized
release-low-footprint reducer.

The current reducer removes only:

```text
LocalVariableTable
LocalVariableTypeTable
```

It preserves line numbers, stack maps, annotations, signatures, bootstrap
methods, module metadata, and application classes outside the admitted scope.
Signed, sealed, and multi-release JARs are skipped by default.

Record the reducer manifest:

```text
V1 input hash
V2 output hash
per-JAR input/output hashes
bytes removed
classes scanned/reduced
skip reasons
preserved-attribute checks
materialization layout
```

V2 must use V1 as its parent. Rebuilding a different optimizer candidate and
calling it V2 breaks the three-leg interpretation.

## Gate

Require zero byte-preservation failures, identical application and Boot loader
layers where V2 is dependency-only, all expected libraries materialized, and
no original JAR shadowing a reduced JAR.
