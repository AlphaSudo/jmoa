# V2-K Doctor Runtime Unblock Gate

This report records the first policy-aware unblock gate after the inventory
gate.

Script:

```text
scripts/doctor-runtime-unblock-gate.ps1
```

Runtime policy:

```text
RetrainD2RCds
```

## Verified Artifacts

```text
corrected D2 fat JAR: present
D2 + raw reducer fat JAR: present
corrected D2 CDS archive: present
D2R raw-reduced CDS archive: not configured
```

Artifact hashes matched the expected published V2-K values:

```text
corrected D2 fat JAR:
1818b210028987085c782df9e5cd8cfbb159b809966d80d0aa0b458c39569b85

D2 + raw reducer fat JAR:
9d00877c0af90e02b0c8d812f8bc659297ca67d6a939016caf96d3d5ced79742

corrected D2 CDS archive:
02955a369bb9f675706e4715e28437220a95637294fc23d07d0a381ad216f6d2
```

## Blockers

```text
D2R_CDS_NOT_TRAINED
MISSING_IMAGE
MISSING_CONFIG
MISSING_DATABASE
MISSING_NETWORK
PORT_BUSY_OR_BLOCKED
BLOCKED_PRIVATE_STACK
```

The gate is blocked before semantic smoke.

## Next Gate

```text
restore private runtime stack inputs
restore/build required images
create runtime network
free or remap blocked database port
train fresh D2R CDS archive
run runtime materialization proof
```

No Doctor semantic, runtime screen, V2-C, V2-D, startup, CDS, or memory claim is
made here.
