# V2-O Runtime Preflight Helper

`jmoa:runtime-preflight` uses V2-N rules but computes SHA-256 for the exact
artifact and optional CDS archive supplied by the caller. It writes:

```text
jmoa-runtime-preflight.json
jmoa-runtime-preflight.md
```

Readiness states include `READY_FOR_SMOKE`, `READY_FOR_SCREEN`, and
`READY_FOR_CONFIRMATION`, plus explicit blocks for missing hashes, policy
mismatch, stale CDS evidence, unavailable runtime stacks, and failed smoke.

```powershell
./scripts/runtime-preflight.ps1 `
  -InputDir <reports-dir> `
  -ArtifactPath <artifact-path> `
  -Service <service> `
  -LaunchMode EXPLODED_BOOT_APP `
  -RuntimePolicy NO_CDS_LOW_DIRTY `
  -Scope PUBLIC
```

For CDS, add `-CdsArchivePath <fresh-archive>`. The helper does not create an
archive or make a claim; it only determines the next allowed gate.
