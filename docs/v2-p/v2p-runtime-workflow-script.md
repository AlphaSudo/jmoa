# V2-P Runtime Workflow Script

`scripts/run-jmoa-runtime-workflow.ps1` has three modes:

- `analyze`: reads supplied gate reports and emits a normalized workflow report.
- `execute`: runs V2-M and V2-N first, then only explicitly configured V2-O
  steps in order.
- `replay`: validates state-machine behavior against a public historical suite.

Use `execute` only with a reviewed JSON configuration that supplies all paths
and explicit step contracts. The script does not invent a launch command,
train CDS implicitly, mutate an artifact, or update the claim register.

An analyze configuration may supply existing reports, or normalized gate values
for replay/compatibility testing:

```json
{
  "service": "spring-petclinic-visits-service",
  "artifact": "baseline-plus-raw",
  "scope": "PUBLIC",
  "reducerEngine": "raw",
  "launchMode": "EXPLODED_BOOT_APP",
  "runtimePolicy": "NO_CDS_LOW_DIRTY",
  "reducerRecommendationReport": "<report>",
  "runtimeRecommendationReport": "<report>",
  "preflightReport": "<report>",
  "materializationProofReport": "<report>",
  "baselineSemanticSmokeReport": "<report>",
  "candidateSemanticSmokeReport": "<report>",
  "screenReport": "<report>",
  "confirmationReport": "<v2c-report>",
  "attributionReport": "<v2d-report>",
  "claimRegisterReference": "V2-L Visits Raw Reducer Runtime Win"
}
```

For `execute`, add reviewed `preflightStep`, `materializationStep`,
`semanticSmokeStep`, `screenStep`, and `confirmationStep` objects. Each object
has a script path and argument array. The coordinator requires the previous
state before it invokes each object.
