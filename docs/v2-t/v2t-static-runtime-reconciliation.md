# V2-T Static/Runtime Reconciliation

The new Maven goal is:

```text
jmoa:analyze-generated-evidence
```

It reads one static V2-A inventory and optional diagnostic captures, produces an
exclusive census plus lifecycle rows, and refuses to label evidence matched when
the static artifact SHA-256 and capture artifact SHA-256 are absent or differ.

Recovered customers evidence contains a V2-A workload diagnostic: eight
runtime-only lambda implementation classes and 813,664 histogram bytes were
observed. That capture has no recorded static/capture artifact hashes and no
startup or warmup capture, so its V2-T status is
`ARTIFACT_FINGERPRINT_MISSING`, not matched evidence.

Doctor D2R has a static inventory but no matching V2-A lifecycle capture. Its
status is also `ARTIFACT_FINGERPRINT_MISSING`. Visits has a public reducer
runtime result but no matching full generated-class inventory/capture pair, so
V2-T does not synthesize a generated-family result for it.

No report has `prototypeAdmitted=true`.
