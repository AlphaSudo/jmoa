# V2-U Cross-Service Generated Matrix

Status: `PARTIAL_INFRASTRUCTURE`

| Family lead | Customers | Visits | Doctor D2R | Current classification |
| --- | --- | --- | --- | --- |
| Lambda / metafactory | Runtime-only workload signal observed, not matched | incomplete | incomplete | `EVIDENCE_INCOMPLETE` |
| Spring Data generated helpers | static surface present | inventory missing | static surface present | `STATIC_ONLY_IN_AVAILABLE_CAPTURES` |
| Spring AOT BeanDefinitions | static/report-only where present | incomplete | static/report-only where present | `GENERATED_MUTATION_BLOCKED` |
| Proxy/CGLIB/ByteBuddy/Hibernate | unsafe/report-only | incomplete | unsafe/report-only | `GENERATED_MUTATION_BLOCKED` |
| Synthetic/bridge-heavy classes | static overlap signal | inventory missing | static overlap signal | `EVIDENCE_INCOMPLETE` |

No family currently qualifies as `MULTI_SERVICE_RUNTIME_RELEVANT` because the
available lifecycle evidence is not fingerprint-matched across services.
