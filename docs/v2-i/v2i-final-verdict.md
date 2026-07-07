# V2-I Final Verdict

V2-I is confirmed as a runtime-positive recovery path for the reducer policy
problem discovered in V2-H.

## Verdict

```text
status: CONFIRMED_RUNTIME_WIN
runtime claim: true
artifact claim: true
```

Claimable wording:

```text
V2-I adds an explicit raw reducer engine that preserves BootstrapMethods while stripping only LocalVariableTable and LocalVariableTypeTable. On Spring PetClinic customers-service under EXPLODED_BOOT_APP, NO_CDS_LOW_DIRTY, MALLOC_ARENA_MAX=1, no CDS/AppCDS/Leyden, and no runtime javaagent, the raw reducer produced a V2-C-confirmed incremental runtime win over full P2.
```

Confirmed result:

```text
materialized dependency jar delta: -3,668,109 bytes
V2-C verdict: CONFIRMED_WIN
paired wins: 2/3
median PSS delta: -4,467 KB
median Private_Dirty delta: -4,208 KB
median memory.current delta: -4,493,312 bytes
```

V2-D attribution:

```text
primary: HEAP_PAGE_TOUCH_REDUCTION
secondary: ANONYMOUS_RW_ALLOCATOR_REDUCTION
not explained as retained-object shrinkage or class-count savings alone
```

## Not Claimed

```text
Doctor runtime win
fat-JAR win
CDS/AppCDS win
startup win
cross-service runtime generalization
BootstrapMethods stripping
all debug metadata stripping
```

## Next Recommended Action

Productize the raw engine cautiously, then run a second-service semantic smoke
or a controlled Doctor runtime unblock plan. Do not start a new reducer type
before raw-engine policy and docs are stable.
