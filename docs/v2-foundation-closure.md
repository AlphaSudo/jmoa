# V2 Foundation Closure

Status: V2-A through V2-F are closed as the current public V2 foundation.

This closure exists so future work starts from a clean claim boundary. V2-F is
now complete as reducer productization and second-service artifact smoke; it is
not still the next phase.

## Closed Milestones

| Milestone | Closed As | Mutation / Claim Boundary |
| --- | --- | --- |
| V2-A | Generated/synthetic/proxy/AOT visibility, runtime attribution, safety taxonomy, ROI infrastructure | Generated-class mutation remains disabled |
| V2-B | Bytecode/classfile footprint profiling, near-64KB risk, runtime correlation | Broad bytecode mutation remains disabled |
| V2-C | Evidence validation, paired confirmation, variance classification, perturbation detection, historical replay | Validation layer only |
| V2-D | Memory attribution, smaps/NMT reconciliation, heap/object/class/metaspace attribution | Explanation layer only |
| V2-E | Opt-in LVT/LVTT reducer, PetClinic artifact smoke, semantic smoke, V2-C confirmation, V2-D attribution | Confirmed only for PetClinic exploded Boot no-CDS protocol |
| V2-F | Signed/MR/sealed JAR safety, reducer manifest, PetClinic hardened artifact smoke, Doctor artifact smoke, admission policy | Productization only; no new runtime claim |

## Current Confirmed Runtime Scope

The only confirmed V2-E reducer runtime claim is:

```text
service: Spring PetClinic customers-service
comparison: full P2 vs full P2 + V2-E LVT/LVTT reducer
launch mode: EXPLODED_BOOT_APP / JarLauncher
runtime policy: NO_CDS_LOW_DIRTY
MALLOC_ARENA_MAX: 1
CDS/AppCDS/Leyden: disabled
runtime javaagent: absent
paired wins: 2/3
median PSS delta: -1,624 KB
median Private_Dirty delta: -1,636 KB
median memory.current delta: -12,255,232 bytes
```

## Artifact-Only Scope

V2-F adds artifact-level evidence only:

```text
PetClinic hardened reducer smoke: 3,870,720 bytes removed
Doctor corrected D2 dependency smoke: 4,156,014 bytes removed
```

Doctor remains artifact-smoke only. There is no Doctor semantic smoke, V2-C
runtime confirmation, V2-D runtime attribution, or Doctor runtime memory claim in
V2-F.

## Still Not Claimed

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

## Next Phase

Next phase:

```text
V2-G - reducer generalization and runtime portability
```

V2-G should test whether the confirmed V2-E LVT/LVTT reducer generalizes to a
second runtime target. It should not implement a new reducer type and should not
broaden metadata stripping.

