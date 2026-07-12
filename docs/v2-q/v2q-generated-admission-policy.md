# V2-Q Generated Family Admission Policy

Only `ORDINARY_APPLICATION` is initially admitted, and only for raw
`LocalVariableTable` and `LocalVariableTypeTable` removal.

| Family | Admission | V2-Q policy |
| --- | --- | --- |
| `ORDINARY_APPLICATION` | `ALLOW_METADATA_ONLY` | Raw metadata-only reduction can run with every explicit safety flag. |
| `SPRING_AOT`, `JAVAC_SYNTHETIC`, `KOTLIN_SYNTHETIC` | `REPORT_ONLY` | Inventory only. |
| `SPRING_CGLIB`, `BYTE_BUDDY`, `HIBERNATE_PROXY` | `BLOCK_SEMANTIC_RISK` | Copy unchanged. |
| `JDK_PROXY`, `LAMBDA` | `BLOCK_RUNTIME_DYNAMIC` | Runtime contract shapes are never reduced. |
| `UNKNOWN_GENERATED` | `BLOCK_UNKNOWN` | Copy unchanged. |

`LineNumberTable`, `SourceFile`, `StackMapTable`, annotations, signatures,
bootstrap methods, nesting, record, and sealed-class metadata remain preserved.
