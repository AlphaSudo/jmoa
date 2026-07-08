# V2-K Doctor Runtime Inventory

Target:

```text
Doctor corrected D2
```

Inventory date:

```text
2026-07-08
```

## Artifact Inventory

| Item | Status | Notes |
| --- | --- | --- |
| Corrected D2 fat JAR | present | Existing corrected D2 source artifact found locally |
| D2 + raw reducer fat JAR | materialized locally | Generated under `target/`; not committed |
| Raw reduced dependency libs | present | V2-J raw artifact smoke output exists under `target/` |
| D2 corrected CDS archive | present | Trained for non-reduced D2 artifact |
| D2R raw-reduced CDS archive | missing | Must be freshly trained if CDS mode is used |
| Doctor runtime compose/scripts | present | Existing private phase32k scripts and compose files found locally |

## Local Materialization Summary

The D2 + raw reducer fat JAR was materialized locally from the corrected D2 fat
JAR plus V2-J raw-reduced dependency libs.

```text
source fat-JAR bytes: 101,906,981
materialized fat-JAR bytes: 97,989,324
BOOT-INF/lib entries: 184
BOOT-INF/lib entries replaced: 184
missing replacement libs: 0
source SHA-256: 1818b210028987085c782df9e5cd8cfbb159b809966d80d0aa0b458c39569b85
materialized SHA-256: 9d00877c0af90e02b0c8d812f8bc659297ca67d6a939016caf96d3d5ced79742
```

This is materialization proof only. It is not runtime proof.

## Runtime Stack Inventory

| Item | Status | Notes |
| --- | --- | --- |
| HMS config-server image | missing locally | Required by existing Doctor compose |
| HMS discovery-server image | missing locally | Required by existing Doctor compose |
| HMS Doctor base image | missing locally | Required to build D2/D2R images |
| HMS Doctor D2-fixed image | missing locally | Must be rebuilt or restored |
| Postgres image | missing locally | Can be pulled/rebuilt, but DB init still needs private SQL |
| Private config repo | required | Existing compose depends on private local config |
| Doctor DB init SQL | required | Existing compose depends on private local DB init |
| Podman | available | Image inventory command ran successfully |

## Current Verdict

```text
MISSING_IMAGE
MISSING_CONFIG
MISSING_DATABASE
CDS_POLICY_UNDECIDED
BLOCKED_PRIVATE_STACK
```

Doctor is not failed and not confirmed. It is blocked until the private runtime
stack and CDS/no-CDS policy are resolved.
