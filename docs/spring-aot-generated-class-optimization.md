# Spring AOT Generated-Class Optimization

JMOA V2-A selects Spring AOT BeanDefinition helper classes as the first
generated-class prototype family.

This is a cautious selection:

- the classes exist at build time,
- they can be scanned without instrumenting the JVM,
- their origins can be verified after materialization,
- they are less identity-sensitive than CGLIB, JDK proxy, ByteBuddy, or
  Hibernate proxy classes.

The current implementation is report-only. It emits:

```text
synthetic-prototype-family-selection.md/json
synthetic-optimizer-prototype-report.md/json
synthetic-affected-classes.json
synthetic-safety-validation.json
```

Allowed current operations:

```text
REPORT_ONLY
REPACK_ONLY
RUNTIME_ORIGIN_VERIFY
DUPLICATE_HELPER_SHAPE_ANALYSIS
```

Blocked current operations:

```text
DELETE
CONSOLIDATE_BYTECODE
REPLACE_WITH_SHARED_ADAPTER
ALTER_BEAN_REGISTRATION_LOGIC
```

Before any bytecode mutation is enabled, a service must pass:

- bytecode verification,
- ApplicationContext startup,
- bean definition count comparison,
- repository count comparison when applicable,
- health endpoint smoke,
- business workload with zero errors,
- runtime-origin verification,
- memory screen with PSS/Private_Dirty not worse,
- paired confirmation before a public claim.
