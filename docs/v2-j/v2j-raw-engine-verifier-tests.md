# V2-J Raw Engine Verifier Tests

V2-J strengthens the raw reducer verifier suite.

Covered fixtures and behaviors:

```text
ordinary debug class
generic class with Signature
class annotation preservation
LineNumberTable preservation
StackMapTable preservation through readable class output
invokedynamic / BootstrapMethods preservation
inner class metadata preservation
nest metadata preservation
record component metadata preservation
module-info.class skip behavior
signed JAR skip policy
multi-release JAR skip policy
sealed JAR skip policy
unsafe strip flag fail-fast behavior
```

Important test behavior:

```text
raw engine removes LVT/LVTT from BootstrapMethods-bearing classes
default asm engine still skips BootstrapMethods-bearing classes
raw auditor accepts classes where only LVT/LVTT changed
raw auditor rejects non-target byte drift
report writer emits raw-reducer-byte-preservation-report.json
report writer emits jmoa-reducer-manifest-v2.json
```

Focused verifier command:

```powershell
mvn -q -pl jmoa-maven-plugin -Dtest=LocalVariableDebugAttributeReducerTest test
```

Result:

```text
passed
```
