# V2-U Lifecycle Reconciliation

Status: `PARTIAL_INFRASTRUCTURE`

V2-U defines these lifecycle classifications:

```text
PACKAGED_NEVER_OBSERVED_IN_CAPTURE
PACKAGED_STARTUP_LOADED
PACKAGED_WARMUP_LOADED
PACKAGED_WORKLOAD_LOADED
RUNTIME_GENERATED_STARTUP
RUNTIME_GENERATED_WARMUP
RUNTIME_GENERATED_WORKLOAD
HISTOGRAM_PERSISTENT
RUNTIME_RELEVANCE_UNKNOWN
```

Current reconciliation:

| Service | Startup | Warmup | Workload | Classification |
| --- | ---: | ---: | ---: | --- |
| customers-service | missing | missing | present, not fingerprinted | `ARTIFACT_FINGERPRINT_MISSING` |
| visits-service | missing | missing | missing | `EVIDENCE_INCOMPLETE` |
| Doctor D2R | missing | missing | missing | `LIFECYCLE_CAPTURE_INCOMPLETE` |

The current evidence is enough to define the lifecycle model and identify gaps.
It is not enough to classify any generated family as prototype-admissible.
