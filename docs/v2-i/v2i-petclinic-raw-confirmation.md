# V2-I PetClinic Raw Confirmation

V2-I confirms the raw reducer as an incremental runtime improvement over full
P2 for PetClinic under the documented public no-CDS protocol.

Comparison:

```text
full P2
vs
full P2 + V2-I raw LocalVariableTable/LocalVariableTypeTable reducer
```

Protocol:

```text
service: Spring PetClinic customers-service
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS/Leyden: off
runtime javaagent: absent
workload: corrected 27 endpoints x 3 rounds
class-load logging during memory pairs: false
```

Result:

```text
V2-C verdict: CONFIRMED_WIN
valid runs: 6/6
paired wins: 2/3
median PSS delta: -4,467 KB
median Private_Dirty delta: -4,208 KB
median memory.current delta: -4,493,312 bytes
median heap PSS delta: -2,332 KB
median anonymous_rw PSS delta: -2,544 KB
median loaded classes delta: -3
workload errors: 0
```

No startup claim is made.
