# V2-R Runtime Relevance

V2-R does not treat static class count as runtime cost. A candidate can be large
in a jar and still irrelevant to the measured workload.

| Surface | Static Signal | Runtime Relevance | Classification |
| --- | --- | --- | --- |
| V2-Q visits application LVT/LVTT | 480 bytes / 4 classes | Confirmation failed | `LOW_ROI_ARTIFACT_ONLY` |
| Spring Data generated dependency family | ~1.38 MB static classfile bytes | Runtime relevance not proven in committed V2-R evidence | `STATIC_ONLY` |
| CGLIB / proxy / ByteBuddy / Hibernate proxy | Present in V2-B/V2-A reports | Semantically risky regardless of footprint | `HIGH_RISK_RUNTIME_RELEVANT` when loaded, otherwise report-only |
| Bouncy Castle / Guava near-64KB methods | Large static dependency methods | Not observed loaded in available PetClinic evidence | `STATIC_ONLY_RISK` |

Runtime promotion still requires:

```text
semantic smoke
single-screen gate
3-pair V2-C confirmation
V2-D attribution
```
