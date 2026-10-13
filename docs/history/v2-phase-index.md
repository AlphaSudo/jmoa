# V2 engineering history

This index describes how the existing product capabilities developed. Internal
phase ledgers are not part of the current public documentation tree. Historical
accepted/rejected measured records remain available with their original scope.

| Historical track | Retained public capability description |
| --- | --- |
| Generated/synthetic inventory and safety | [Generated inventory](../generated-class-optimizer.md), [safety model](../synthetic-class-safety-model.md) |
| Classfile footprint and near-limit methods | [Size profiler](../bytecode-size-profiler.md), [64 KiB risk](../method-64kb-risk.md) |
| Evidence validation and attribution | [Evidence engine](../evidence-engine.md), [memory attribution](../methodology/memory-attribution.md) |
| Dependency reducer and preservation | [Reducer configuration](../reference/reducer-configuration.md), [bytecode architecture](../architecture/bytecode-transformation.md) |
| Runtime recommendation and preflight | [Policy model](../architecture/runtime-policy-model.md), [schema examples](../reference/schema-index.md) |
| Generated/application admission limits | [Decisions](../decisions/generated-family-disposition.md), [negative results](../results/negative-results.md) |

## Publication milestones

Earlier V1/V2 matrices answer historical engineering questions. Stable 2.1
publishes the accepted PetClinic R41F-versus-B0 deployment result: 14.88 MiB
process PSS and 16.24 MiB cgroup saving, including packaging and bounded costs.
Provisional 2.15 publishes the Patient Java 26/Leyden stopped-PSS observation:
8.38 MiB median saving and conditional lower bound 8.19 MiB, with failed original
calibration admitted by exception and unqualified or failed performance bounds.
It publishes no new Maven binaries.

Use the [current release record](../releases/current-state.md) and
[claim register](../v2-claim-register.md) to distinguish current claims from
historical development maturity. No future implementation schedule is published here.
