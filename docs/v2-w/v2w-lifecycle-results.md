# V2-W Lifecycle Results

All three bundles reached `MATCHED_DIAGNOSTIC_EVIDENCE` with complete startup,
warmup, and workload attribution and matching identity tuples.

| Family | Matched services | Classification | Packaged | Startup loaded | Warmup new | Workload new |
| --- | ---: | --- | ---: | ---: | ---: | ---: |
| Lambda/metafactory | 3 | multi-protocol runtime relevant | 10,935 | 10,283 | 306 | 99 |
| Spring Data generated | 3 | multi-protocol runtime relevant | 873 | 610 | 32 | 3 |
| Synthetic/bridge | 3 | multi-protocol runtime relevant | 26,564 | 6,491 | 233 | 70 |
| Anonymous inner class | 3 | multi-protocol runtime relevant | 12,958 | 2,413 | 86 | 21 |
| Spring AOT BeanDefinitions | 1 | safety-blocked runtime relevant | 322 | 946 | 0 | 0 |
| Spring CGLIB | 3 | safety-blocked runtime relevant | 12 | 19 | 0 | 0 |
| JDK proxy | 3 | safety-blocked runtime relevant | 0 | 573 | 4 | 0 |

The runtime-only lambda signal reproduced in all three services and remains in
the existing lambda optimizer domain. Spring Data helpers are loaded across
both protocols, but no bounded semantics-preserving consolidation was found.
AOT and proxy families remain safety-blocked despite runtime relevance.
