# Phase V2-A1 Generated Class Inventory

Status: implemented as static inventory scanner.

## Scanner

Implementation package:

```text
com.yourorg.jmoa.plugin.generated
```

Primary classes:

```text
GeneratedClassInventoryScanner
GeneratedClassClassifier
GeneratedClassSafetyModel
GeneratedClassInventoryReportWriter
GeneratedClassOptimizer
```

## Inputs

The scanner reads:

- planned JMOA class roots,
- `target/spring-aot/main/classes` when present,
- `target/BOOT-INF/classes` when present,
- explicitly supplied synthetic jar paths,
- optimized dependency jars under `jmoa-optimized-libs`,
- packaged Spring Boot jars when present,
- nested `BOOT-INF/lib/*.jar` entries inside fat jars.

## Outputs

```text
generated-class-inventory.json
generated-class-inventory.md
generated-class-family-breakdown.json
generated-class-inventory-summary.csv
```

## Families

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

## Current Limitation

This is static inventory. Runtime attribution with class-load logs, JFR,
class histograms, metaspace, and smaps belongs to V2-A2.

