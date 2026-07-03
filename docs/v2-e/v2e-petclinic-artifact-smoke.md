# V2-E PetClinic Artifact Smoke

This is an artifact-level smoke for the first V2-E reducer prototype. It is not
a service-level runtime confirmation and does not support a memory claim.

## Input

```text
service: Spring PetClinic customers-service
source evidence shape: Phase 33M full P2 exploded Boot dependency libs
reducer mode: release-low-footprint
mutation: enabled explicitly
stripped attributes: LocalVariableTable, LocalVariableTypeTable
```

## Safety Behavior

```text
LineNumberTable: preserved
SourceFile: preserved
StackMapTable: preserved
annotations: preserved
Signature: preserved
BootstrapMethods: classes skipped in mutation mode
```

## Result

```text
jars processed: 162
classes scanned: 54,196
classes reduced: 12,697
classes skipped for BootstrapMethods: 1,480
original jar bytes: 92,466,274
estimated removable local-variable metadata bytes: 13,508,782
reduced jar bytes: 87,070,377
removed jar bytes: 5,417,754
```

## Claim Boundary

This smoke proves the reducer can produce smaller dependency jars under the
explicit release-low-footprint gate. It does not prove application behavior,
startup, PSS, Private_Dirty, or memory.current improvement.

The next gate is a PetClinic P2 versus P2+Reducer service screen with V2-C
validation and V2-D attribution.
