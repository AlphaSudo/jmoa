# 05 Prove Artifact Lineage

Create one private freeze record before measurement. It must bind B0, V1, and V2 to:

```text
source revision
artifact bytes and hash kind
image ID
application fingerprint
dependency fingerprint
loader fingerprint
JMOA runtime identity
materialization proof
runtime policy
JDK fingerprint
workload identity
configuration hash
```

The generic runner supports two concrete artifact kinds:

- `FILE`: SHA-256 of one file.
- `DIRECTORY_TREE`: SHA-256 over ordered relative paths and each file hash.

Keep historical product IDs in separate provenance fields when they were not calculated with the current concrete hash algorithm.

Run a freeze-only check with:

```powershell
./scripts/run-three-artifact-balanced-campaign.ps1 `
  -ConfigPath <private-config.json> `
  -OutputDirectory <new-freeze-directory> `
  -Stage All `
  -DryRun
```
