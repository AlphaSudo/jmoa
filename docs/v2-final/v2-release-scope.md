# JMOA V2 Release Scope

Status: scope frozen for final audit.

V2 is the productized release of the JMOA evidence, attribution, runtime-policy,
workflow, and raw bytecode-footprint reducer work. It is not a continuation of
the lettered research sequence.

## In Scope

- Build-time V1/full-P2 lambda and adapter optimization.
- Opt-in raw `LocalVariableTable` / `LocalVariableTypeTable` dependency reducer.
- Raw byte-preservation auditor and reducer manifest v2.
- Evidence validation, paired confirmation, variance classification, and
  historical replay.
- Memory attribution across smaps, NMT, heap, anonymous mappings, class/metaspace,
  loaded classes, and histogram evidence.
- Reducer recommendation and runtime-policy recommendation.
- Runtime preflight, materialization proof, semantic smoke, screen, and
  confirmation workflow helpers.
- Generated/synthetic/proxy/AOT inventory, runtime relevance, safety taxonomy,
  matched lifecycle capture, and discovery-only closure.
- Confirmed narrow runtime claims already recorded in the V2 claim register.

## Out Of Scope For V2

- Generated-family bytecode mutation.
- CGLIB, JDK proxy, ByteBuddy, Hibernate proxy, or Spring AOT rewriting.
- Large-method splitting.
- Constant-pool or `BootstrapMethods` reduction.
- Annotation, `StackMapTable`, `LineNumberTable`, `Signature`, or framework
  metadata stripping.
- Universal Spring Boot memory-win claims.
- Startup-win claims.
- Automatic runtime policy application.
- Broad AppCDS, Leyden, Kubernetes, or deployment mutation automation.

## Scope Rule

No new V2 optimizer or research phase should be opened before `v2.0.0-rc1`.
Only P0/P1 release blockers, reproducibility defects, documentation contradictions,
schema compatibility problems, and packaging issues should be fixed.

