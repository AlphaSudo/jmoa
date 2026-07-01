# V2-A Implementation Handoff

Branch:

```text
codex/v2-a-generated-inventory
```

Commit:

```text
235ad9f Add V2-A generated class inventory
```

## Objective

V2-A starts the generated/synthetic/proxy/AOT class optimizer work without
rewriting any generated classes. The current branch implements V2-A0 and V2-A1:

- safety flags,
- inventory-only boundary,
- static generated-class scanner,
- family classification,
- UNKNOWN-by-default risk model,
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
mvn -q -pl jmoa-runtime-lib,jmoa-maven-plugin clean test
./scripts/check-publication-safety.ps1
```

All passed.

## Next Recommended Work

1. Run V2-A1 inventory on PetClinic customers-service.
2. Run V2-A1 inventory on doctor-service if private source is available locally.
3. Add V2-A2 runtime attribution:
   - parse `-Xlog:class+load=info`,
   - correlate with static inventory,
   - parse `GC.class_histogram`,
   - add family-level runtime loaded/instance/bytes counts.
4. Keep all CGLIB/JDK proxy/ByteBuddy/Hibernate families report-only.
5. Select the first optimizer family only after runtime attribution and safety
   taxonomy are written.

