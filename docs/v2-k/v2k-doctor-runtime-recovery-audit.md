# V2-K Doctor Runtime Recovery Audit

The Doctor recovery audit checked whether the old runnable Doctor phase assets
can be used to unblock the new D2 + raw reducer runtime target.

Outcome:

```text
BLOCKED_WITH_ROOT_CAUSE
```

## What Was Found

Legacy runtime assets are present:

```text
Dockerfile.d2-fixed
docker-compose.32k-d2-fixed.yml
docker-compose.32k-train.yml
build-and-test-image.ps1
train-cds.ps1
validate-cds.ps1
```

Those legacy assets are not public-safe as-is. They contain local/private
runtime wiring and credential markers, so they cannot be committed or treated as
portable V2-K runtime infrastructure.

## Verified Artifacts

```text
corrected D2 fat JAR: present
D2 + raw reducer fat JAR: present
corrected D2 CDS archive: present
D2R raw-reduced CDS archive: not configured
```

## Blocking Verdict

```text
LEGACY_RUNTIME_ASSETS_FOUND
LEGACY_ASSETS_REQUIRE_SANITIZATION
D2R_CDS_NOT_TRAINED
MISSING_PRIVATE_INPUT
MISSING_IMAGE
MISSING_NETWORK
```

Doctor is blocked by runtime stack recovery, not by a reducer runtime failure.
No semantic smoke or runtime screen was attempted.
