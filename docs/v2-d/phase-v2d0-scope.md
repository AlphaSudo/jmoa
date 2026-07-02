# V2-D0 Scope And Product Boundary

Status: implemented as report-only memory attribution infrastructure.

V2-D is the JMOA memory causality layer. It runs after V2-C evidence validation
and explains why memory moved. It does not mutate bytecode, change runtime
policy, or claim a new optimizer win.

## Position In V2

```text
V2-A: generated/synthetic shape visibility
V2-B: bytecode/classfile footprint visibility
V2-C: evidence validity and confirmation
V2-D: memory movement attribution
```

## Maven Goal

```text
jmoa:attribution
```

## Feature Flags

```text
jmoa.attribution.enabled=false
jmoa.attribution.mode=analyze
jmoa.attribution.inputDir=<evidence-dir>
jmoa.attribution.outputDir=<output-dir>
jmoa.attribution.requireV2CValid=true
jmoa.attribution.includeJfr=false
jmoa.attribution.includeAsyncProfiler=false
jmoa.attribution.includeJol=false
jmoa.attribution.diagnosticOnly=true
jmoa.attribution.generatedClassReport=<optional-v2a-report>
jmoa.attribution.bytecodeRuntimeCorrelationReport=<optional-v2b-report>
```

## Implemented Outputs

```text
jmoa-memory-attribution.json
jmoa-memory-attribution.md
jmoa-category-deltas.json
jmoa-smaps-nmt-reconciliation.json
jmoa-heap-object-attribution.json
jmoa-causal-hypotheses.json
jmoa-top-object-deltas.csv
```

## Boundary

```text
optimizer mutation: disabled
bytecode mutation: disabled
generated-class mutation: disabled
diagnostic profilers: optional metadata only
claim status: attribution/explanation only
```

Future V2-A generated-class mutation and V2-B bytecode reducers must still pass
semantic safety gates, V2-C confirmation, and V2-D attribution before any public
performance claim.
