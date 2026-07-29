# Choose A JMOA Mode

JMOA exposes two different product paths. Do not transfer evidence between
them.

## Metadata Reduction

Choose reducer-only mode when you want the lowest-risk artifact transformation:

```text
B0R = clean B0 + raw LocalVariableTable/LocalVariableTypeTable reduction
```

Use `jmoa:reduce-bytecode` with the opt-in raw
`release-low-footprint` profile. This mode does not need the V1 runtime library
or generated adapters. It trades local-variable debugging metadata for smaller
class files while preserving line numbers, verification frames, annotations,
signatures, and bootstrap methods.

Reducer-only mode still requires materialization proof, semantic smoke, V2-C
validation, and V2-D attribution before a runtime claim.

## Full Optimization

Choose full optimization only when all of these are true:

```text
the transformed artifact and runtime origins are proven
the target workload represents production behavior
runtime-relevant V1 activation is demonstrated
the direct B0 -> V2 gate passes
```

Full optimization is:

```text
V1 = lambda/adapter semantic transformation
V2 = V1 + metadata reduction
```

An incremental `V1 -> V2` win does not prove the complete product beats clean
B0. Measure all three artifacts in one balanced protocol.

## Decision

```text
Need the lowest-risk artifact-only optimization?
  -> Use reducer-only mode.

Have exact runtime-relevant V1 activation and acceptable V1 cost?
  -> Evaluate full optimization.

Is activation unknown, low, or is B0 -> V1 overhead positive?
  -> Do not promote full V1 mode; use reducer-only or run a separately labeled
     MECHANISM_ACTIVATION_STUDY.

Did direct B0 -> V2 pass the frozen claim gates?
  -> Publish only that exact service/runtime/workload scope.
```

The current unified evidence supports one complete-product win (Doctor) and
two directional but non-confirmed reductions (Patient and PetClinic). It does
not support a universal full-optimization recommendation.
