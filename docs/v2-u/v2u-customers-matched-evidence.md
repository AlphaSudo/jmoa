# V2-U Customers Matched Evidence

Status: `PARTIAL_INFRASTRUCTURE`

The available customers-service evidence is still the recovered V2-T historical
bundle, not a fresh V2-U matched lifecycle bundle.

Current usable signals:

```text
unique generated-like classes: 12,152
unique generated-like classfile bytes: 81,625,377
runtime-only lambda implementation classes observed in workload capture: 8
histogram bytes in recovered workload capture: 813,664
```

Missing for V2-U admission:

```text
static artifact SHA-256
capture artifact SHA-256
startup attribution capture
warmup attribution capture
full V2-U identity tuple
generated-lifecycle-manifest.json
```

Verdict:

```text
ARTIFACT_FINGERPRINT_MISSING
prototypeAdmitted=false
```

The eight observed runtime-only lambda implementations remain useful as a
future capture target. They do not admit a new generated-family prototype.
