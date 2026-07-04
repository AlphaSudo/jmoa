# V2-F Jar Safety Report

V2-F hardens the V2-E reducer before broader product use. The reducer still strips only `LocalVariableTable` and `LocalVariableTypeTable`, but it now avoids dependency surfaces that can be unsafe to rewrite casually.

## Default Policy

```text
signed jars: skip by default
multi-release jars: skip by default
sealed jars: skip by default
module-info.class: preserve
classes with BootstrapMethods: skip in mutation mode
ZIP timestamp policy: preserve
```

Preserved attributes:

```text
LineNumberTable
SourceFile
StackMapTable
RuntimeVisibleAnnotations
RuntimeInvisibleAnnotations
Signature
BootstrapMethods
module-info.class
```

## Artifact Smoke Summary

| Surface | Jars | Classes | Reduced classes | Removed bytes | Signed skipped | Multi-release skipped | Sealed skipped | BootstrapMethods classes skipped |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| PetClinic full P2 dependency libs | 162 | 54,196 | 23,680 | 3,870,720 | 1 | 20 | 1 | 6,029 |
| Doctor corrected D2 dependency libs | 184 | 58,924 | 25,181 | 4,156,014 | 1 | 23 | 0 | 6,761 |

The PetClinic byte reduction is lower than the original V2-E artifact smoke because V2-F now skips signed, multi-release, and sealed jars. That is intentional: V2-F favors a safer default product boundary over maximum byte removal.

Doctor results are artifact-smoke only. They prove the reducer can process a second real dependency surface under the new safety policy, not that Doctor has a runtime memory win.

