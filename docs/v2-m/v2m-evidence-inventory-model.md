# V2-M Evidence Inventory Model

V2-M normalizes reducer and runtime evidence into one admission record before
applying policy.

Canonical inputs:

```text
reducer-build-report.json
raw-reducer-byte-preservation-report.json
v2f-jar-safety-report.json
jmoa-reducer-manifest-v2.json
jmoa-semantic-smoke.json
jmoa-runtime-screen.json
jmoa-paired-confirmation.json
jmoa-evidence-validation.json
jmoa-memory-attribution.json
```

An explicit `reducer-admission-input.json` takes precedence when present in the
input directory. This is useful for CI, historical replay, or systems that
already normalize their evidence.

Core fields:

| Group | Fields |
| --- | --- |
| Runtime identity | service, launch mode, runtime policy |
| V2-B sizing | attribute report presence, debug bytes, LVT bytes |
| Artifact | bytes removed, classes reduced, engine, stripped attributes |
| Safety | raw audit present, failed audits, signed/MR/sealed skips, unsafe requested attributes |
| Semantics | semantic smoke result and runtime screen verdict |
| Confirmation | V2-C verdict, V2-D presence, confirmed service/mode/policy |
| Scope | public, private, internal, or unknown |

Service labels are normalized for punctuation and spacing only. Launch mode and
runtime policy remain exact normalized enum-like values. Thus
`Spring PetClinic visits-service` and `spring-petclinic-visits-service` match,
but `EXPLODED_BOOT_APP` and `SPRING_BOOT_FAT_JAR` do not.

V2-M emits the normalized input alongside its recommendation so the decision is
auditable.

The V2-B attribute report is sizing enrichment. A realized raw reducer report
can still support admission when no service-specific V2-B scan is available.
