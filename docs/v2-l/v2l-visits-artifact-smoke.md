# V2-L Visits Raw Reducer Artifact Smoke

Status:

```text
PASSED
```

The productized raw engine ran with the `release-low-footprint` profile and
removed only `LocalVariableTable` and `LocalVariableTypeTable` attributes.

Result:

```text
dependency JARs processed: 161
static classes scanned: 54,079
classes reduced and byte-audited: 29,701
failed byte-preservation audits: 0
artifacts reduced: 139
signed JARs skipped: 1
multi-release JARs detected: 21
multi-release JARs skipped: 20
sealed JARs skipped: 1
```

Footprint:

```text
baseline dependency JAR bytes: 92,299,443
reduced dependency JAR bytes: 88,783,843
materialized compressed-byte delta: -3,515,600
raw reducer class-entry bytes removed: 3,532,027
```

The two byte totals use different accounting layers: the materialized delta is
the final compressed JAR footprint, while `3,532,027` is the reducer's class-entry
removal accounting.

Preservation gates:

```text
LineNumberTable preserved
SourceFile preserved
StackMapTable preserved
annotations preserved
Signature preserved
BootstrapMethods preserved by the raw engine
module-info.class preserved
```
