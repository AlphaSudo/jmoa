# Debug Attribute Stripping

V2-B measures debug-attribute footprint but does not strip anything yet.

Measured debug-related attributes:

```text
LineNumberTable
LocalVariableTable
LocalVariableTypeTable
SourceFile
SourceDebugExtension
```

Potential future reducer:

```text
LOW_MEMORY_RELEASE_ARTIFACT
```

Possible first mutation candidate:

```text
strip LocalVariableTable and LocalVariableTypeTable only
```

Still blocked:

```text
LineNumberTable
SourceFile
StackMapTable
RuntimeVisibleAnnotations
Signature
BootstrapMethods
InnerClasses
```

Reason:

debug stripping can harm stack traces, source correlation, diagnostics,
observability, and developer experience. It must be opt-in and validated per
deployment mode.
