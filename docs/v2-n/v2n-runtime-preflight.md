# V2-N Runtime Preflight

Preflight uses the same policy rules as analyze mode but treats missing runtime
gates as explicit blockers or screen requirements.

For a CDS request, the minimum gates are:

```text
artifact SHA-256
CDS archive SHA-256
CDS enabled
semantic smoke
runtime materialization proof
CDS mapped-at-runtime proof
V2-C confirmation
V2-D attribution
```

For a no-CDS request, CDS, AppCDS, Leyden, and any runtime javaagent must be
off. A preflight also asks for an artifact SHA-256 or deployment-layer
fingerprint before relying on historical scope.

Example missing-archive decision:

```text
decision: BLOCK_CDS_ARCHIVE_MISMATCH
reason: artifact hash differs from the archive-trained variant
next action: train a fresh variant-specific CDS archive and verify it maps at runtime
```
