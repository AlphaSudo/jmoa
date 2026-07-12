# V2-R Candidate Ranking

V2-R ranks future work by ROI, runtime relevance, and semantic risk.

| Rank | Candidate | ROI Signal | Runtime Relevance | Risk | Next Gate |
| ---: | --- | --- | --- | --- | --- |
| 1 | Spring Data generated/helper family | ~1.38 MB static family bytes | Unknown in committed evidence | Medium | Runtime relevance capture |
| 2 | Spring AOT helper families | Present in Doctor static context | Unknown | Medium/high | Report-only inventory plus smoke model |
| 3 | Application-class LVT/LVTT in visits-service | 480 bytes / 4 classes | Confirmation failed | Low | Do not pursue for visits |
| 4 | CGLIB/JDK proxy/ByteBuddy/Hibernate proxy | Potentially runtime-relevant | Semantically risky | High | Mutation blocked |
| 5 | Near-64KB dependency methods | Static danger-zone methods | Not observed loaded in PetClinic | Medium | Static risk report only |

Recommended next action:

```text
collect runtime relevance for generated families before opening V2-S mutation
```

No candidate is admitted to mutation by V2-R.
