# V2-I Raw Reducer Implementation

V2-I adds an explicit reducer engine:

```text
-Djmoa.reducer.engine=raw
```

The default remains:

```text
-Djmoa.reducer.engine=asm
```

The `raw` engine is narrower than ASM classfile round-tripping. It parses the
classfile byte stream, rewrites only method `Code` attributes, removes nested
`LocalVariableTable` and `LocalVariableTypeTable` attributes, and copies
unrelated classfile structures unchanged.

Preserved surfaces:

```text
constant pool
LineNumberTable
SourceFile
StackMapTable
annotations
Signature
BootstrapMethods
InnerClasses
NestHost/NestMembers
Record
PermittedSubclasses
module-info.class
```

Still skipped by policy:

```text
signed jars
multi-release jars
sealed jars
module-info.class
```

The raw engine exists to test whether BootstrapMethods-bearing classes can be
reduced without rewriting or stripping BootstrapMethods metadata. It is still
opt-in, release-low-footprint only, and disabled by default.
