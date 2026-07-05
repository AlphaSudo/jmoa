# V2-G Target Selection

Selected target:

```text
Doctor corrected D2
```

Why:

```text
Doctor corrected D2 is the strongest second-service target.
V2-F already proved artifact-level reducer savings on its dependency surface.
Historical corrected D2 runtime acceptance exists.
```

Fallbacks considered:

```text
another public PetClinic microservice:
  deferred because it would require creating a fresh optimized target artifact

PetClinic fat-JAR diagnostic:
  deferred because it is launch-mode diagnostic, not second-service generalization
```

V2-G rules:

```text
no new reducer type
no LineNumberTable stripping
no StackMapTable stripping
no annotation stripping
no Signature stripping
no BootstrapMethods rewriting or stripping
use only the existing V2-E LVT/LVTT reducer
```

