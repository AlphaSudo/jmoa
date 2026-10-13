# JMOA documentation

## Current publications

**Stable 2.1.0** ships Maven plugin/runtime artifacts and the accepted PetClinic
result: 14.88 MiB lower process PSS and 16.24 MiB lower target-cgroup RAM, including
packaging and measured CPU/startup costs. **Provisional 2.15** adds Patient
Java 26/Leyden evidence: 8.38 MiB lower median stopped-process PSS, conditional
lower bound 8.19 MiB. Original calibration failed with an approved exception;
performance bounds remain unqualified or failed. Startup/cache warmth and
production/uploaded-binary parity are unqualified; no new Maven binaries ship.

- [Current release boundaries](releases/current-state.md)
- [Stable 2.1 release](https://github.com/AlphaSudo/jmoa/releases/tag/v2.1.0)
- [Provisional 2.15 release](https://github.com/AlphaSudo/jmoa/releases/tag/v2.15)
- [2.1 PetClinic paper](paper/jmoa-v2.1-petclinic-memory-engineering.md)
- [2.15 Patient companion paper](paper/jmoa-v2.15-patient-leyden-memory-evidence.md)

## Engineering documentation

| Area | Start here |
| --- | --- |
| Adoption | [Evaluation guide](adoption/README.md) |
| Architecture | [System overview](architecture/system-overview.md), [bytecode](architecture/bytecode-transformation.md), [materialization](architecture/materialization-and-origin-proof.md), [runtime policy](architecture/runtime-policy-model.md) |
| Methodology | [Measurement](methodology/measurement-protocol.md), [statistics](methodology/statistics-and-acceptance.md), [attribution](methodology/memory-attribution.md), [CDS](methodology/cds-and-runtime-policy.md) |
| Decisions | [Generated families](decisions/generated-family-disposition.md), [application reducer](decisions/application-reducer-rejection.md), [historical AppCDS](decisions/patient-appcds-rejection.md) |
| Product engineering | [Lambda/adapter costs](product-engineering/v1-overhead-roadmap.md) |
| Evidence | [Current and historical records](product-evidence/README.md) |
| Reference | [Goals](reference/maven-plugin-goals.md), [reducer configuration](reference/reducer-configuration.md), [compatibility](reference/compatibility-matrix.md), [schemas/examples](reference/schema-index.md) |
| Reproduction | [PetClinic quickstart](reproduction/petclinic-quickstart.md), [generic confirmation](reproduction/extended-confirmation.md), [interpretation](reproduction/interpreting-results.md) |
| Release audit | [Audit boundaries](release-audit/README.md) |
| Results | [Results by scope](results/README.md), [negative results](results/negative-results.md) |
| Runtime studies | [Historical CDS/AppCDS studies](runtime-policy-studies/README.md) |
| History | [Development index](history/v2-phase-index.md), [historical final audit](v2-final/README.md), [claim register](v2-claim-register.md) |

Private implementation ledgers and personal drafts are outside this public
documentation tree. Historical measurements retain their original comparator
and verdict; internal next-step plans are not publication material.
