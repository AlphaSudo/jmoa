# Phase V2-A3 Safety Taxonomy

Status: implemented.

The safety taxonomy classifies generated-like classes into transform safety
categories:

```text
SAFE_TO_CONSOLIDATE
SAFE_TO_REPLACE_WITH_SHARED_ADAPTER
SAFE_TO_MOVE_TO_AOT
SAFE_TO_REPACK_ONLY
SAFE_TO_DEFER_TO_CDS
UNSAFE_RUNTIME_SEMANTIC
UNKNOWN
```

Current policy:

- Spring CGLIB, JDK proxy, ByteBuddy, and Hibernate proxy families are
  `UNSAFE_RUNTIME_SEMANTIC`.
- Spring AOT BeanDefinition and registration helpers are `SAFE_TO_REPACK_ONLY`.
- LambdaMetafactory sites defer to the existing v1 lambda admission pipeline.
- Spring Data generated helpers and compiler synthetic/bridge helpers remain
  `UNKNOWN`.

The plugin writes:

```text
generated-class-safety-taxonomy.json
generated-class-safety-taxonomy.md
generated-class-transform-eligibility.json
```
