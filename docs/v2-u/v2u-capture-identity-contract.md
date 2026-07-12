# V2-U Capture Identity Contract

V2-U extends the V2-T SHA gate into a full identity tuple. A static inventory and
diagnostic lifecycle bundle may be reconciled only when all fields are present
and equal:

```text
artifactSha256
service
launchMode
runtimePolicy
reducerEngine
familyRegistryVersion
scannerVersion
```

## Analyzer Statuses

```text
MATCHED_DIAGNOSTIC_EVIDENCE
ARTIFACT_FINGERPRINT_MISSING
ARTIFACT_FINGERPRINT_MISMATCH
IDENTITY_FIELD_MISSING
SERVICE_MISMATCH
RUNTIME_SCOPE_MISMATCH
REDUCER_ENGINE_MISMATCH
REGISTRY_VERSION_MISMATCH
SCANNER_VERSION_MISMATCH
LIFECYCLE_CAPTURE_INCOMPLETE
```

`MATCHED_DIAGNOSTIC_EVIDENCE` remains diagnostic only. It does not create a
runtime-memory claim and it does not admit mutation without a separate semantic
and confirmation plan.
