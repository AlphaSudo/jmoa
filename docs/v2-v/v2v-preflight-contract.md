# V2-V Capture Preflight Contract

`capture-generated-lifecycle.ps1` writes
`generated-capture-preflight.json` before it launches or attaches to a JVM.

The preflight blocks when the artifact is missing, the declared identity is
incomplete, the output directory is dirty without `-ReuseOutput`, or an
expected artifact fingerprint does not match.

Possible states are:

```text
READY
BLOCK_ARTIFACT_MISSING
BLOCK_IDENTITY_INCOMPLETE
BLOCK_ARTIFACT_FINGERPRINT_MISMATCH
BLOCK_OUTPUT_NOT_CLEAN
BLOCK_ARTIFACT_ORIGIN_UNPROVEN
```

Origin proof is recorded after a PID is available. A container capture records
the declared container/image context; a host JVM capture attempts to match the
process command line to the expected artifact.
