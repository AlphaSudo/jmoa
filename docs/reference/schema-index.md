# Schema and example index

Stable Maven coordinates remain `com.yourorg.jmoa:jmoa-maven-plugin:2.1.0`.
Provisional 2.15 publishes evidence without adding a new Maven report engine.
Inspect metadata/schema versions; unsupported or missing fields must not be
silently treated as passing evidence.

| Domain | Implementation / description | Reviewed example |
| --- | --- | --- |
| Evidence validation and replay | `EvidenceModels`, [evidence engine](../evidence-engine.md) | [Historical replay contract](examples/historical-evidence-replay.json) |
| Attribution | `AttributionModels`, [methodology](../methodology/memory-attribution.md) | [Historical Doctor verdict](examples/historical-doctor-verdict.json) |
| Generated-family safety | `generated/*`, [safety](../synthetic-class-safety-model.md) | [Admission example](examples/generated-family-admission.json) |
| Size reporting | `size/*`, [size profiler](../bytecode-size-profiler.md) | [Report-only example](examples/size-profiler-closure.json) |
| Reducer preservation | `reducer/*`, [configuration](reducer-configuration.md) | [Preservation example](examples/application-preservation-report.json) |
| Reducer recommendation | `recommendation/*` | [Input](examples/reducer-admission-input.json), [historical replay](examples/historical-reducer-replay.json) |
| Runtime recommendation/preflight | `runtimepolicy/*`, [policy model](../architecture/runtime-policy-model.md) | [Registry](runtime-protocol-registry.json), [input](examples/runtime-policy-admission-input.json), [replay](examples/historical-runtime-replay.json) |
| Workflow state machine | `scripts/run-v2-workflow.ps1` | [Historical replay](examples/historical-workflow-replay.json) |
| Current release claims | [Release boundaries](../releases/current-state.md) | [PetClinic JSON](../product-evidence/petclinic-r41f-b0-t7r23-result.json), [Patient JSON](../product-evidence/patient-scoped-ram-benchmark.json) |

The relocated examples preserve their historical schema/data. They are tooling
fixtures, not new benchmark measurements or automatic 2.15 policy admissions.
Raw private captures are not included. Phase-ledger folders are not required
for the retained default registry and example replay paths.
