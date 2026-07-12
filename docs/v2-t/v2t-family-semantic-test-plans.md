# V2-T Family Semantic Test Plans

No generated family is admitted in V2-T. Future family-specific work must first
have a matched static artifact and startup/warmup/workload diagnostic capture,
then pass the corresponding semantic plan.

| Family | Required semantic proof | V2-T status |
| --- | --- | --- |
| Spring AOT BeanDefinitions | ApplicationContext bean count, registration graph, endpoint smoke | Report-only |
| Spring Data generated | repository count, entity/property accessor behavior, query smoke | Report-only |
| Lambda/metafactory | serialization and captured-argument behavior where applicable | Report-only |
| CGLIB/JDK proxy/ByteBuddy/Hibernate proxy | interception, transaction/security, identity and reflection behavior | Blocked from mutation |
| Synthetic/bridge/Kotlin/nestmate | dispatch and generic bridge behavior | Report-only |

Passing a semantic smoke alone does not admit a mutation. V2-C confirmation and
V2-D attribution remain mandatory after any future candidate reaches the
conjunctive admission gate.
