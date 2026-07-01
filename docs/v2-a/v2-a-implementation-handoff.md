# V2-A Implementation Handoff

Branch:

```text
codex/v2-a-generated-inventory
```

Base commit:

```text
235ad9f Add V2-A generated class inventory
```

## Objective

V2-A starts the generated/synthetic/proxy/AOT class optimizer work without
rewriting any generated classes. The current branch implements the V2-A
infrastructure through report-only prototype selection:

- safety flags,
- inventory-only boundary,
- static generated-class scanner,
- family classification,
- UNKNOWN-by-default risk model,
- runtime attribution from class-load logs and class histograms,
- safety taxonomy and transform eligibility,
- Spring AOT BeanDefinition helper prototype family selection,
- generated-class ROI V2 feature report,
- report writer,
- tests,
- docs.

## Current Feature Flags

```text
jmoa.synthetic.enabled=false
jmoa.synthetic.inventoryOnly=true
jmoa.synthetic.optimizeFamily=none
jmoa.synthetic.failOnUnsafe=true
jmoa.synthetic.scanClasspathJars=false
jmoa.synthetic.jarPaths=<optional semicolon/newline separated jars>
jmoa.synthetic.classLoadLog=<optional -Xlog:class+load file>
jmoa.synthetic.classHistogram=<optional jcmd GC.class_histogram output>
```

Default behavior remains V1 lambda optimization. The synthetic/generated-class
inventory runs only when `jmoa.synthetic.enabled=true`.

## Reports

When enabled, the Maven plugin writes:

```text
target/generated-class-inventory.json
target/generated-class-inventory.md
target/generated-class-family-breakdown.json
target/generated-class-inventory-summary.csv
target/generated-class-safety-taxonomy.json
target/generated-class-safety-taxonomy.md
target/generated-class-transform-eligibility.json
target/synthetic-prototype-family-selection.json
target/synthetic-prototype-family-selection.md
target/synthetic-optimizer-prototype-report.json
target/synthetic-optimizer-prototype-report.md
target/synthetic-affected-classes.json
target/synthetic-rewritten-classes.json
target/synthetic-safety-validation.json
target/jmoa-roi-v2-report.json
target/jmoa-roi-v2-report.md
```

When runtime evidence is supplied:

```text
target/generated-class-runtime-attribution.json
target/generated-class-runtime-attribution.md
target/generated-class-origin-map.json
target/generated-class-survival-report.md
```

## Implemented Families

```text
PLAIN
LAMBDA_METAFATORY_SITE
SPRING_CGLIB
SPRING_AOT_BEAN_DEFINITIONS
SPRING_AOT_REGISTRATION
SPRING_DATA_GENERATED
JDK_PROXY
BYTEBUDDY
HIBERNATE_PROXY
SYNTHETIC_BRIDGE_METHODS
COMPILER_SYNTHETIC_HELPER
```

## Verification Completed

Local checks run with Temurin JDK 26 and Maven 3.9.9:

```powershell
mvn -q -pl jmoa-maven-plugin -Dtest=GeneratedClassInventoryScannerTest test
mvn -q -pl jmoa-maven-plugin -Dtest=LambdaDeduplicationMojoIntegrationTest#optimizeGoalWritesGeneratedClassInventoryWhenSyntheticInventoryEnabled test
mvn -q -pl jmoa-maven-plugin -Dtest=GeneratedClassRuntimeAttributorTest,GeneratedClassSafetyTaxonomyBuilderTest test
mvn -q -pl jmoa-maven-plugin test
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin test
./scripts/check-publication-safety.ps1
```

All passed.

## Local Service Smokes

PetClinic exploded Boot Phase 33 artifact:

```text
roots scanned: 3
jars scanned: 162
classes scanned: 54,326
generated-like records: 12,152
generated runtime-loaded classes attributed: 8
```

Doctor corrected fat JAR Phase 32 artifact:

```text
classes scanned: 59,424
generated-like records: 14,469
families reported: 9
```

These are scanner/attribution smokes, not generated-class memory-win claims.

## Next Recommended Work

1. Keep all generated-class bytecode mutation disabled until semantic gates are
   automated.
2. Add a mutation-enabled Spring AOT helper prototype only after bean-count,
   endpoint behavior, runtime-origin, and memory screen gates pass.
3. Keep CGLIB/JDK proxy/ByteBuddy/Hibernate families report-only.
4. Run paired service confirmation only after a mutation-enabled prototype
   exists.
