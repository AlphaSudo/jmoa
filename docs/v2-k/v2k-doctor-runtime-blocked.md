# V2-K Doctor Runtime Blocked

Doctor runtime remains blocked after the V2-K inventory pass.

Blocked reasons:

```text
HMS Doctor runtime images are missing locally
HMS config/discovery/base Doctor images are missing locally
Postgres image is not currently present locally
private config repo is required by existing compose
private Doctor database init SQL is required by existing compose
existing D2 CDS archive targets the non-reduced D2 artifact
D2R raw-reduced CDS archive has not been trained
```

This is not a runtime failure. No semantic smoke or memory screen was attempted.

## Required Unblock Work

```text
restore/build HMS runtime images
restore private config and DB init inputs
decide CDS policy
train fresh D2R CDS archive if CDS path is selected
prove runtime image contains the D2R raw-reduced artifact
then run semantic smoke
```

## Fallback

If the private Doctor stack remains blocked, proceed with the public
Spring PetClinic visits-service lane while keeping Doctor as an active blocker.
