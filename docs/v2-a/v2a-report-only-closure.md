# V2-A Report-Only Closure

Status: closed as generated-class visibility and safety infrastructure.

V2-A is not closed as a generated-class mutation optimizer. That remains
intentionally disabled until semantic gates and V2-C evidence gates are applied.

## Scope

```text
feature area: generated/synthetic/proxy/AOT class visibility
mutation enabled: false
optimizer mode: report-only
closed milestone: inventory, attribution, taxonomy, ROI signals
```

## Verified Outputs

When enabled, V2-A produces the report-only generated-class evidence layer:

```text
generated-class-inventory.json
generated-class-inventory.md
generated-class-inventory-summary.csv
generated-class-runtime-attribution.json
generated-class-safety-taxonomy.json
generated-class-transform-eligibility.json
synthetic-prototype-family-selection.json
jmoa-roi-v2-report.json
```

## Service Smoke Summary

| Service | Deployment shape | Classes scanned | Generated-like records | Runtime attribution |
| --- | --- | ---: | ---: | --- |
| Spring PetClinic customers-service | EXPLODED_BOOT_APP | 54,326 | 12,152 | 8 generated runtime-loaded classes attributed from Phase 33 evidence |
| Doctor-service | corrected Spring Boot fat JAR | 59,424 | 14,469 | runtime attribution unavailable in the sanitized public docs |

## Family Visibility

PetClinic showed generated-like footprint across lambda/metafactory sites,
Spring Data generated helpers, ByteBuddy/Hibernate proxy indicators, and
synthetic/bridge method families.

Doctor showed the same broad families plus Spring CGLIB and Spring AOT
BeanDefinition/registration families.

## Closure Boundary

V2-A is closed only as:

```text
generated class inventory
runtime attribution when logs/histograms exist
safety taxonomy
transform eligibility reporting
ROI feature reporting
report-only prototype family selection
```

The following remain blocked:

```text
generated-class bytecode mutation
Spring AOT BeanDefinition helper rewrite
CGLIB proxy rewrite
JDK proxy rewrite
ByteBuddy/Hibernate proxy rewrite
generated-class memory-win claim
```

Future V2-A mutation must pass semantic service checks and V2-C evidence gates.
