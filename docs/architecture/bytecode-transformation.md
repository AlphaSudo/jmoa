# Bytecode Transformation

JMOA has two shipped mutation families with separate admission and verification
rules.

## Lambda And Adapter Rewriting

`LambdaScanner` identifies stateless `LambdaMetafactory` sites and records
owners, descriptors, bootstrap arguments, and implementation handles. Profile,
framework, access, and ROI inputs are converted into a concrete
`LambdaWeavingPlan`. Depending on the admitted tier, JMOA references a shared
runtime adapter or generates a package-local adapter. The weaver replaces only
planned sites and verifies that no admitted `invokedynamic` remains unexpectedly.

Capturing, serializable, unsafe-access, unsupported bootstrap, and unobserved
sites can be excluded. Report-only output explains each denial.

## Raw LVT/LVTT Reduction

The productized raw reducer removes only:

- `LocalVariableTable`;
- `LocalVariableTypeTable`.

It preserves line numbers, stack-map frames, annotations, signatures,
BootstrapMethods, nest metadata, records, sealed-class metadata, method code,
constant-pool semantics, and every other non-target structure. The
`RawReducerBytePreservationAuditor` normalizes classfiles and fails the artifact
when a non-target difference is found.

This is not literal whole-file identity: the target attributes intentionally
differ, and repackaging may change ZIP ordering, timestamps, compression, or
container metadata. The invariant is semantic classfile equivalence outside the
admitted target structures.

## JAR Safety And Scope

The reducer is disabled by default. Mutation requires all of:

```text
jmoa.reducer.enabled=true
jmoa.reducer.reportOnly=false
jmoa.reducer.optimize=true
jmoa.reducer.profile=release-low-footprint
jmoa.reducer.stripLocalVariableTable=true
jmoa.reducer.stripLocalVariableTypeTable=true
jmoa.reducer.engine=raw
```

Signed, multi-release, and sealed JARs are skipped by the hardened artifact
policy. `module-info.class` is preserved. Artifact include/exclude rules bound
the dependency surface, and services may exclude `jmoa-runtime-lib` so the
support library is byte-identical between variants.

Unsafe flags for line numbers, stack maps, annotations, signatures, or
BootstrapMethods fail rather than silently broadening mutation. Partial output
is not promotable after a reducer or preservation-audit failure.

Generated/proxy family mutation and application-class reduction are not shipped
runtime features. Their reports remain discovery or rejected-candidate evidence.
