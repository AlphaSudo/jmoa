# Generated Class Optimizer

JMOA V2-A starts by making generated JVM class shapes visible. It does not
rewrite generated/proxy/AOT classes by default.

The first implementation is inventory-only and is controlled by:

```text
-Djmoa.synthetic.enabled=false
-Djmoa.synthetic.inventoryOnly=true
-Djmoa.synthetic.optimizeFamily=none
-Djmoa.synthetic.failOnUnsafe=true
```

To write generated-class inventory reports during a plugin run:

```powershell
mvn process-classes `
  -Djmoa.synthetic.enabled=true `
  -Djmoa.synthetic.inventoryOnly=true `
  -Djmoa.synthetic.optimizeFamily=none
```

Outputs are written under `target/`:

```text
generated-class-inventory.json
generated-class-inventory.md
generated-class-family-breakdown.json
generated-class-inventory-summary.csv
```

## What Is Detected

The V2-A1 scanner inventories:

- lambda/metafactory call-site indicators,
- Spring CGLIB and enhancer class patterns,
- Spring AOT `__BeanDefinitions` and registration helpers,
- Spring Data generated accessor/repository/AOT-like helpers,
- JDK dynamic proxy patterns,
- ByteBuddy and Hibernate proxy indicators,
- synthetic and bridge methods,
- compiler helpers such as `access$`, `lambda$`, and `$deserializeLambda$`.

## Safety Boundary

Every generated class starts with `riskLevel=UNKNOWN`. A family label is not a
license to optimize it. Later V2-A phases must add runtime attribution and a
safety taxonomy before any transformation can be enabled.

