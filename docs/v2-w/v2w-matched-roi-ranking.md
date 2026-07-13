# V2-W Matched-Evidence ROI Ranking

| Family | Coverage | Admission |
| --- | ---: | --- |
| Lambda/metafactory | 3 services, 2 protocols | `GENERATED_REPORT_ONLY` (existing lambda domain) |
| Spring Data generated | 3 services, 2 protocols | `GENERATED_REPORT_ONLY` |
| Synthetic/bridge methods | 3 services, 2 protocols | `GENERATED_REPORT_ONLY` |
| Anonymous inner classes | 3 services, 2 protocols | `GENERATED_REPORT_ONLY` |
| Spring AOT helpers | Doctor only | `GENERATED_MUTATION_BLOCKED` |
| CGLIB/JDK proxy/ByteBuddy/Hibernate proxy | 3 services | `GENERATED_MUTATION_BLOCKED` |
| Kotlin synthetic | Doctor only, static-only | `APPLICATION_LOW_ROI_ARTIFACT_ONLY` |

Static size does not override semantic risk or the absence of a bounded transform.
