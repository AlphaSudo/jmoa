# V2-L Visits Final Verdict

Final status:

```text
CLOSED_CONFIRMED
```

V2-L confirms the productized raw LVT/LVTT reducer on a second public runtime
target.

Confirmed claim:

```text
On Spring PetClinic visits-service revision
305a1f13e4f961001d4e6cb50a9db51dc3fc5967, the raw reducer produced a confirmed
incremental runtime win over the same unreduced baseline under the documented
exploded-Boot, no-CDS, low-dirty protocol.
```

Confirmed metrics:

```text
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
paired wins: 3/3
median PSS delta: -2,012 KB
median Private_Dirty delta: -1,680 KB
median memory.current delta: -1,712,128 bytes
dependency-layer compressed-byte delta: -3,515,600 bytes
```

Attribution:

```text
primary movement: anonymous_rw PSS and NMT-visible metaspace reduction
not primary: retained-object reduction, class-count reduction, or heap PSS
```

Claim boundary:

```text
public visits-service only
baseline vs baseline + raw reducer, not full P2 vs full P2 + reducer
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY with MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
embedded HSQLDB standalone protocol
no startup claim
```

Not claimed:

```text
all Spring PetClinic services
all Spring Boot services
fat-JAR or CDS/AppCDS improvement for visits-service
startup improvement
generated-class mutation
large-method splitting
constant-pool rewriting
BootstrapMethods rewriting or stripping
metadata stripping beyond LVT/LVTT
```

Release tag after merge:

```text
v0.10.0-v2l-public-runtime-confirmed
```
