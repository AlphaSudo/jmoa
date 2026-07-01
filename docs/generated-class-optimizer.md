# Generated Class Optimizer

JMOA V2-A starts by making generated JVM class shapes visible. It does not
rewrite generated/proxy/AOT classes by default.

The implementation is still bytecode-mutation-safe by default. It inventories
generated classes, can attribute runtime generated-class cost from logs, writes
a safety taxonomy, selects the first prototype family, and emits a generated
class ROI feature report. It does not rewrite generated/proxy/AOT classes.

The feature boundary is controlled by:

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
generated-class-safety-taxonomy.json
generated-class-safety-taxonomy.md
generated-class-transform-eligibility.json
synthetic-prototype-family-selection.json
synthetic-prototype-family-selection.md
synthetic-optimizer-prototype-report.json
synthetic-optimizer-prototype-report.md
synthetic-affected-classes.json
synthetic-rewritten-classes.json
synthetic-safety-validation.json
jmoa-roi-v2-report.json
jmoa-roi-v2-report.md
```

If runtime evidence files are supplied, the plugin also writes:

```text
generated-class-runtime-attribution.json
generated-class-runtime-attribution.md
generated-class-origin-map.json
generated-class-survival-report.md
```

Runtime attribution inputs:

```powershell
mvn process-classes `
  -Djmoa.synthetic.enabled=true `
  -Djmoa.synthetic.inventoryOnly=true `
  -Djmoa.synthetic.optimizeFamily=none `
  -Djmoa.synthetic.classLoadLog=<path-to-Xlog-class-load.log> `
  -Djmoa.synthetic.classHistogram=<path-to-GC-class-histogram.txt>
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

## Runtime Attribution

The runtime attributor consumes `-Xlog:class+load` logs and `jcmd
GC.class_histogram` output. It reports generated-like runtime-loaded classes,
runtime-only generated classes, histogram instance bytes, load origins, and
family-level survival categories.

This is attribution, not admission. A family that loads or allocates is still
unsafe to transform until the safety taxonomy admits a specific operation.

## Safety Boundary

Every generated class starts with `riskLevel=UNKNOWN`. A family label is not a
license to optimize it. Runtime proxy families are report-only. Spring AOT
BeanDefinition helpers are the selected first prototype family, but only for
repack/origin verification and duplicate-shape analysis until semantic gates
are automated.
