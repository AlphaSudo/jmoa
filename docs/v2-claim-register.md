# V2 Claim Register

This is the source of truth for public V2 claims after V2-F.

## Confirmed Runtime Claims

### 1. PetClinic Full P2 No-CDS Win

JMOA full P2 confirmed a public no-CDS PetClinic memory win under:

```text
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
```

Claim summary:

```text
median PSS reduction: about 4.6 MB
source: Phase 33M public case study
```

### 2. V2-E Incremental PetClinic Reducer Win

V2-E confirmed an incremental runtime win over full P2:

```text
comparison: full P2 vs full P2 + V2-E LVT/LVTT reducer
paired wins: 2/3
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
```

Scope:

```text
Spring PetClinic customers-service
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
```

## Artifact-Only Claims

```text
V2-F PetClinic hardened artifact smoke:
  removed bytes: 3,870,720
  runtime claim: false

V2-F Doctor corrected D2 artifact smoke:
  removed bytes: 4,156,014
  runtime claim: false

V2-G Doctor corrected D2 artifact generalization:
  removed dependency-jar bytes: 4,156,014
  BOOT-INF/lib entries replaced in materialized fat JAR: 184
  runtime claim: false
```

## Not Claimed

```text
startup win
fat-JAR runtime win
CDS/AppCDS runtime win
Doctor runtime win
cross-service runtime generalization
all debug metadata stripping safety
LineNumberTable stripping
StackMapTable stripping
annotation stripping
Signature stripping
BootstrapMethods rewriting or stripping
CGLIB/JDK proxy rewriting
Spring AOT generated-class mutation
```

Any new runtime performance claim must pass V2-C validation and V2-D attribution.
