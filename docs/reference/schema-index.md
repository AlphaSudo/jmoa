# Schema Index

JMOA reports use versioned JSON documents. The Java records in the plugin are
the implementation source; this index points to the authoritative public
descriptions and representative frozen records.

| Domain | Implementation / description | Representative record |
| --- | --- | --- |
| Evidence run, capture, pair, confirmation | `EvidenceModels`, [Evidence Engine](../evidence-engine.md) | [Patient base-CDS verdict](../v2-final/patient-base-cds-final-verdict.json) |
| Memory attribution | `AttributionModels`, [Memory Attribution](../methodology/memory-attribution.md) | [Doctor verdict](../v2-k/v2k-doctor-final-verdict.json) |
| Generated inventory and safety | `generated/*`, [Generated Class Optimizer](../generated-class-optimizer.md) | [Generated-family admission](../v2-w/v2w-prototype-admission.json) |
| Bytecode size | `size/*`, [Bytecode Size Profiler](../bytecode-size-profiler.md) | [Profiler closure](../v2-b/v2b-report-only-closure.json) |
| Reducer report and preservation audit | `reducer/*`, [Reducer Configuration](reducer-configuration.md) | [Application preservation report](../v2-q/v2q-application-byte-preservation-report.json) |
| Reducer recommendation | `recommendation/*` | [Reducer admission input](../v2-m/reducer-admission-input.json) |
| Runtime-policy recommendation/preflight | `runtimepolicy/*`, [Runtime Policy Model](../architecture/runtime-policy-model.md) | [Runtime admission input](../v2-n/runtime-policy-admission-input.json) |
| Final claims | Frozen release docs | [Three-service matrix](../v2-final/v2-three-service-memory-matrix.json) |

Consumers should inspect `metadataVersion` and fail on unsupported shapes rather
than silently accepting missing fields. Paths and raw private captures are not
part of the public schema contract.
