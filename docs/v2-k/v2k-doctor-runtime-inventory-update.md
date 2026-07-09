# V2-K Doctor Runtime Inventory Update

This update was produced from the executable inventory script:

```text
scripts/doctor-runtime-inventory.ps1
```

Run date:

```text
2026-07-09
```

## Result

```text
Podman: present
corrected D2 fat JAR: present
D2 + raw reducer fat JAR: present
corrected D2 CDS archive: present
D2R raw-reduced CDS archive: not configured
required runtime images: missing
private config input: not configured
database init SQL: not configured
runtime network: missing
port 5432: busy or blocked
```

## Artifact Hashes

```text
corrected D2 fat JAR SHA-256:
1818b210028987085c782df9e5cd8cfbb159b809966d80d0aa0b458c39569b85

D2 + raw reducer fat JAR SHA-256:
9d00877c0af90e02b0c8d812f8bc659297ca67d6a939016caf96d3d5ced79742

corrected D2 CDS archive SHA-256:
02955a369bb9f675706e4715e28437220a95637294fc23d07d0a381ad216f6d2
```

## Verdict

```text
MISSING_IMAGE
MISSING_CONFIG
MISSING_DATABASE
D2R_CDS_NOT_TRAINED
BLOCKED_PRIVATE_STACK
```

Doctor remains blocked, not failed. No semantic smoke or runtime memory screen
was attempted.
