# V2 Claim Register

This is the source of truth for public V2 claims after the V2-L visits-service
runtime confirmation.

Closure terms follow:

- [V2 Phase Closure Taxonomy](v2-phase-closure-taxonomy.md)

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
earlier V2-E reducer policy used for v0.7.0
```

This claim is not transferred to the later V2-F-hardened/productized reducer.

### 3. V2-I Raw Reducer PetClinic Win

V2-I confirmed an incremental runtime win over full P2 using the explicit raw
reducer engine:

```text
comparison: full P2 vs full P2 + V2-I raw LVT/LVTT reducer
paired wins: 2/3
median PSS delta: -4,467 KB
median Private_Dirty delta: -4,208 KB
median memory.current delta: -4,493,312 bytes
```

Scope:

```text
Spring PetClinic customers-service
EXPLODED_BOOT_APP / JarLauncher
NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
jmoa.reducer.engine=raw
```

This claim is separate from the older V2-E claim and from the V2-F/V2-H
hardened `asm` reducer result.

### 4. V2-K Doctor Raw Reducer Runtime Win

V2-K confirmed an incremental runtime win for the private Doctor corrected D2
runtime:

```text
comparison: D2 + fresh current-runtime D2 CDS vs D2R + fresh D2R CDS
launch mode: SPRING_BOOT_FAT_JAR
runtime policy: CDS
paired wins: 3/3
median PSS delta: -5,156 KB
median Private_Dirty delta: -5,212 KB
median memory.current delta: -6,975,488 bytes
```

Scope:

```text
private Doctor corrected D2 runtime stack
raw-reduced D2R artifact
variant-specific CDS archives
Java 26 container runtime
class-load logging disabled during memory pairs
JFR disabled
```

This claim is not public-reproducible and is not transferred to all Doctor
deployments, all fat-JAR services, all CDS/AppCDS modes, or startup performance.

### 5. V2-L Visits Raw Reducer Runtime Win

V2-L confirmed the productized raw reducer on a second public runtime target:

```text
comparison: public visits baseline vs the same baseline + raw LVT/LVTT reducer
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
paired wins: 3/3
median PSS delta: -2,012 KB
median Private_Dirty delta: -1,680 KB
median memory.current delta: -1,712,128 bytes
```

Scope:

```text
Spring PetClinic visits-service
public source revision 305a1f13e4f961001d4e6cb50a9db51dc3fc5967
MALLOC_ARENA_MAX=1
no CDS/AppCDS/Leyden
no runtime javaagent
embedded HSQLDB standalone protocol
jmoa.reducer.engine=raw
```

No visits-specific full-P2 artifact was available. This claim is baseline vs
baseline plus reducer, not full P2 vs full P2 plus reducer. It is not transferred
to all PetClinic/Spring Boot services, fat-JAR/CDS modes, or startup performance.

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

V2-H PetClinic hardened/productized reducer screen:
  materialized dependency jar byte delta: -3,855,370
  BOOT-INF/lib entries replaced: 162
  screen PSS delta: +7,804 KB
  screen Private_Dirty delta: +7,824 KB
  runtime claim: false

V2-I PetClinic raw reducer materialization:
  materialized dependency jar byte delta: -3,668,109
  BOOT-INF/lib entries replaced: 162
  runtime claim: true under the V2-I PetClinic scope above

V2-J Doctor raw artifact smoke:
  removed dependency-jar bytes: 3,926,870
  classes reduced and audited: 31,942
  failed raw byte-preservation audits: 0
  runtime claim: false

V2-K Doctor raw materialization:
  BOOT-INF/lib entries replaced: 184/184
  materialized raw D2R fat-JAR SHA-256 recorded
  runtime claim: true under the V2-K Doctor scope above

V2-L visits raw materialization:
  materialized dependency jar byte delta: -3,515,600
  BOOT-INF/lib entries replaced: 161/161
  classes reduced and byte-audited: 29,701
  failed byte-preservation audits: 0
  runtime claim: true under the V2-L visits scope above
```

## Not Claimed

```text
V2-F-hardened/productized asm reducer runtime win
startup win
public Doctor reproducibility
all Doctor deployments
all fat-JAR services
all CDS/AppCDS runtime modes
universal public cross-service generalization
all Spring PetClinic or Spring Boot services
visits-service fat-JAR or CDS/AppCDS improvement
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
