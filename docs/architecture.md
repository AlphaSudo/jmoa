# Architecture

JMOA is a build-time JVM optimization pipeline for Spring Boot services.

The core flow is:

```text
profile -> ROI/admission -> rewrite -> adapter generation -> materialize artifact -> verify runtime origins -> measure
```

## Components

### Maven Plugin

`jmoa-maven-plugin` owns scanning, candidate admission, bytecode rewriting,
adapter generation, optimized dependency packaging, adapter reference
validation, and measurement-report support.

### Runtime Library

`jmoa-runtime-lib` is the small runtime surface used by rewritten bytecode. It
is included as a normal library dependency, not as a javaagent.

### Tools And Examples

The `tools` and `examples` areas document launch helpers, deployment
materialization, runtime-origin verification, and the public PetClinic no-CDS
workflow.

## Claim Boundary

JMOA should not claim success at build time alone. A claim is valid only after:

1. optimized bytecode is materialized into the actual deployment shape,
2. adapter references validate,
3. runtime origins prove optimized classes are loaded,
4. the expected CDS/no-CDS mode is verified,
5. workload errors are zero,
6. PSS, Private_Dirty, and cgroup memory are measured.
