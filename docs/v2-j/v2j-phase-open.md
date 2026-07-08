# V2-J Phase Open

V2-J starts after `v0.8.0-v2i-raw-reducer-confirmed`.

Goal:

```text
Productize the V2-I raw reducer engine without adding new reducer types.
```

V2-J keeps the reducer scope narrow:

```text
strip LocalVariableTable
strip LocalVariableTypeTable
preserve LineNumberTable
preserve SourceFile
preserve StackMapTable
preserve annotations
preserve Signature
preserve BootstrapMethods
preserve InnerClasses / nest / record metadata
```

The V2-I PetClinic runtime claim remains scoped to:

```text
Spring PetClinic customers-service
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
jmoa.reducer.engine=raw
```

V2-J does not create a Doctor runtime claim, fat-JAR claim, CDS/AppCDS claim, or
cross-service runtime generalization claim.
