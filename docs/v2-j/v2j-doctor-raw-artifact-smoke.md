# V2-J Doctor Raw Artifact Smoke

The V2-J raw reducer was run against the Doctor corrected D2 dependency libs.

Comparison:

```text
Doctor corrected D2 dependency libs
vs
Doctor corrected D2 dependency libs + raw LVT/LVTT reducer
```

Result:

```text
engine: raw
jars processed: 184
classes scanned: 58,924
classes reduced: 31,942
raw byte-preservation audits: 31,942
failed audits: 0
BootstrapMethods classes skipped: 0
signed jars skipped: 1
multi-release jars skipped: 23
sealed jars skipped: 0
original dependency-jar bytes: 100,970,980
reduced dependency-jar bytes: 97,061,899
compressed dependency-jar bytes removed: 3,926,870
```

V2-G ASM artifact smoke removed `4,156,014` compressed dependency-jar bytes.
V2-J raw processed more classes and skipped no BootstrapMethods-bearing classes,
but the compressed dependency-jar byte delta is lower. This is an artifact-level
observation only; compressed JAR bytes do not directly predict runtime memory.

## Claim Boundary

This smoke proves the raw engine can process a second real dependency surface
with byte-preservation auditing. It does not prove Doctor runtime behavior,
startup behavior, CDS/AppCDS behavior, V2-C confirmation, V2-D attribution, or a
Doctor memory win.
