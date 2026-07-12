# V2-U Matched Family ROI Ranking

Status: `PARTIAL_INFRASTRUCTURE`

V2-U changes the ranking rule:

```text
No matched runtime evidence => maximum decision GENERATED_REPORT_ONLY
Matched runtime evidence + safety blocker => GENERATED_MUTATION_BLOCKED
Matched runtime evidence + meaningful ROI + bounded semantics => CANDIDATE_FOR_PROTOTYPE
```

Current ranking:

| Rank | Family lead | Static signal | Matched runtime evidence | Decision |
| ---: | --- | ---: | --- | --- |
| 1 | Lambda / metafactory | meaningful | no | `GENERATED_REPORT_ONLY` |
| 2 | Spring Data generated helpers | meaningful | no | `GENERATED_REPORT_ONLY` |
| 3 | Synthetic/bridge-heavy classes | meaningful | no | `GENERATED_REPORT_ONLY` |
| 4 | Spring AOT BeanDefinitions | meaningful | no | `GENERATED_MUTATION_BLOCKED` |
| 5 | Proxy/CGLIB/ByteBuddy/Hibernate | meaningful | no | `GENERATED_MUTATION_BLOCKED` |

Static size no longer dominates the decision. Runtime relevance must be matched
to the same artifact and lifecycle bundle.
