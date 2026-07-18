# System Overview

JMOA is a Maven-plugin-centered optimization and evidence system. It has four
product boundaries: analysis/transformation, deployment materialization,
runtime proof, and evidence admission.

## Modules

| Module | Responsibility |
| --- | --- |
| `jmoa-maven-plugin` | Scanning, admission, weaving, reduction, reports, recommendations, evidence validation, and attribution |
| `jmoa-runtime-lib` | Java 17-compatible `Function`, `Supplier`, `Consumer`, `BiConsumer`, and `Predicate` adapters referenced by admitted rewrites |
| `scripts` | Spring Boot materialization, hash/origin proof, semantic smoke, paired capture, and policy automation |
| `examples` | Public PetClinic build and semantic-smoke workflow |

## Transformation Pipeline

`ClassRootPlanner` identifies application, expanded dependency, and optimized
dependency roots. `ClassFileWalker` and `LambdaScanner` read classfiles with ASM.
`LambdaFilter`, framework safety policy, profile inputs, and ROI logic decide
which sites are admitted. `LambdaWeavingPlanBuilder` produces explicit targets;
`LambdaWeavingCoordinator` applies them; `LambdaWeaveSanityChecker` and
`AdapterReferenceValidator` reject incomplete output.

`deduplicate-lambdas` is the principal V1 transformation goal. `optimize` is its
configured lifecycle wrapper. `reduce-bytecode` is the independent V2 metadata
reducer. Keeping these goals separate prevents debug-metadata mutation from
being silently enabled by lambda optimization.

## Deployment Pipeline

Optimized dependency JARs are emitted separately from the source artifact.
Materialization then replaces matching `BOOT-INF/lib` entries in a fat JAR or
an exploded Boot dependency layer. The workflow records input/output hashes,
replacement counts, runtime-library identity, and deployment mode. It fails on
missing or shadowed replacements.

## Evidence Pipeline

`EvidenceMojo` invokes `EvidenceEngine`, which parses run manifests, smaps,
cgroup, NMT, heap, histogram, and workload captures. `EvidenceValidator`
invalidates polluted runs before `EvidencePairAnalyzer` calculates medians.
`AttributionMojo` invokes `MemoryAttributionEngine` only after V2-C-valid
evidence by default.

Recommendation goals consume reviewed evidence rather than infer a universal
policy:

- `recommend-reducer` evaluates a registered reducer/service/protocol result;
- `recommend-runtime` selects among registered runtime-policy results;
- `runtime-preflight` proves artifact/archive/policy compatibility before a
  screen is allowed.

## End-To-End Invariants

1. The compared artifacts are frozen by SHA-256.
2. Only admitted structures may change.
3. The deployed artifact must contain the intended replacements.
4. The live JVM must prove the expected artifact and runtime policy.
5. The semantic workload must finish with zero errors.
6. Only valid paired evidence enters medians.
7. A claim is scoped to the tested service, artifact, deployment, and policy.

See [Bytecode Transformation](bytecode-transformation.md),
[Materialization and Origin Proof](materialization-and-origin-proof.md), and
[Measurement Protocol](../methodology/measurement-protocol.md).
