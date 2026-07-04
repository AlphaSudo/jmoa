# V2-E PetClinic Final Verdict

Verdict: **confirmed runtime reducer**.

V2-E is now confirmed for the public PetClinic customers-service protocol:

```text
full P2
vs
full P2 + V2-E reducer
```

The reducer remains narrow:

```text
LocalVariableTable
LocalVariableTypeTable
```

It still does not strip:

```text
LineNumberTable
SourceFile
StackMapTable
RuntimeVisibleAnnotations
RuntimeInvisibleAnnotations
Signature
BootstrapMethods
InnerClasses
NestHost / NestMembers
Record
PermittedSubclasses
```

## Confirmed Claims

```text
dependency jar footprint delta: -5,395,897 bytes
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
pairs: 3
paired wins: 2/3
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
workload errors: 0
```

## Attribution

V2-D attribution says the result is not a simple heap-used or class-count story.

```text
heap PSS median delta: +1,572 KB
heap used median delta: +145 KB
anonymous_rw PSS median delta: -2,456 KB
NMT total committed median delta: -2,074 KB
NMT metaspace committed median delta: -3,271 KB
```

So the correct explanation is:

```text
V2-E produced a confirmed incremental runtime memory win under the PetClinic
exploded Boot no-CDS protocol. The movement is explained mainly by NMT-visible
and anonymous_rw reductions, not by retained-object reduction or class-count
savings.
```

## Claim Boundary

Do not claim:

```text
startup win
fat-JAR win
CDS/AppCDS win
cross-service generalization
all debug metadata is safe to strip
memory win from LineNumberTable stripping
memory win from StackMapTable stripping
```

## Release Boundary

Recommended tag:

```text
v0.7.0-v2e-runtime-confirmed
```

Next phase:

```text
V2-F = generalize reducer to a second service or productize the release-low-footprint reducer profile.
```
