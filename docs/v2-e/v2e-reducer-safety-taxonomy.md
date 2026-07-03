# V2-E Reducer Safety Taxonomy

V2-E treats classfile attributes as runtime or diagnostic contracts unless they
are explicitly admitted.

| Attribute | Category | V2-E Action |
| --- | --- | --- |
| `LocalVariableTable` | `SAFE_OPT_IN_RELEASE` | Strip only in explicit release-low-footprint mode |
| `LocalVariableTypeTable` | `SAFE_OPT_IN_RELEASE` | Strip only in explicit release-low-footprint mode |
| `LineNumberTable` | `UNSAFE_DIAGNOSTIC_CRITICAL` | Preserve |
| `SourceFile` | `UNSAFE_DIAGNOSTIC_CRITICAL` | Preserve |
| `StackMapTable` | `UNSAFE_VERIFICATION` | Preserve |
| `RuntimeVisibleAnnotations` | `UNSAFE_FRAMEWORK_SEMANTIC` | Preserve |
| `RuntimeInvisibleAnnotations` | `UNSAFE_FRAMEWORK_SEMANTIC` | Preserve |
| `Signature` | `UNSAFE_FRAMEWORK_SEMANTIC` | Preserve |
| `BootstrapMethods` | `UNSAFE_FRAMEWORK_SEMANTIC` | Skip class in mutation mode |
| `InnerClasses`, `NestHost`, `NestMembers`, `Record`, `PermittedSubclasses` | `UNSAFE_FRAMEWORK_SEMANTIC` | Preserve |

Unsafe strip flags fail fast with:

```text
UNSAFE_REDUCER_NOT_IMPLEMENTED
```

This is deliberate. V2-E is not a general debug stripping tool and does not
strip line numbers, stack-map frames, annotations, signatures, or invokedynamic
metadata.
