# 04 Build V2

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
