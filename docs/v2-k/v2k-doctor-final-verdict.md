# V2-K Doctor Final Verdict

Final status:

```text
CLOSED_CONFIRMED_DOCTOR
```

V2-K successfully moved Doctor from artifact-only/runtime-blocked to confirmed
runtime evidence.

What changed from earlier V2-K:

```text
private runtime inputs were located locally
support images were rebuilt locally
fresh D2 CDS archive was trained for the current Java runtime
fresh D2R CDS archive was trained for the raw-reduced artifact
D2 and D2R both passed semantic smoke
single runtime screen passed
3-pair V2-C confirmation passed
V2-D attribution passed
```

Confirmed claim:

```text
Doctor corrected D2 plus raw-reduced D2R is a confirmed incremental runtime win
over corrected D2 under the recovered private Spring Boot fat-JAR/CDS protocol.
```

Confirmed metrics:

```text
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
paired wins: 3/3
median PSS delta: -5,156 KB
median Private_Dirty delta: -5,212 KB
median memory.current delta: -6,975,488 bytes
```

Attribution:

```text
primary movement: anonymous_rw and mapped-file PSS reduction
supporting movement: NMT class/metaspace reduction and class-count savings
not primary: retained heap-object reduction
```

Not claimed:

```text
public reproducibility
all Doctor deployments
all fat-JAR services
all CDS/AppCDS modes
startup improvement
generated-class mutation
large-method splitting
constant-pool rewriting
BootstrapMethods rewriting or stripping
```

Suggested release tag after merge:

```text
v0.9.0-v2k-doctor-runtime-confirmed
```
