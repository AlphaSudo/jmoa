# V2-K Doctor Image Rebuild Plan

The inventory script showed that required Doctor runtime images are not present
locally.

Required image roles:

```text
config server
discovery server
database
Doctor base image
Doctor corrected D2 image
Doctor D2 + raw reducer candidate image
```

## Candidate Image Requirement

The Doctor D2 + raw reducer image must contain the materialized raw artifact:

```text
D2 + raw reducer fat JAR SHA-256:
9d00877c0af90e02b0c8d812f8bc659297ca67d6a939016caf96d3d5ced79742
```

## Required Proof Before Smoke

```text
image exists
image app JAR SHA-256 matches materialized D2R artifact
BOOT-INF/lib count is 184
sample BOOT-INF/lib hashes match reducer manifest
old non-reduced D2 artifact does not shadow D2R
if CDS path is selected, D2R CDS archive hash is recorded
runtime command points at expected artifact/archive
```

## Current Status

```text
MISSING_IMAGE
MISSING_CONFIG
MISSING_DATABASE
D2R_CDS_NOT_TRAINED
```

The policy-aware unblock gate additionally reports:

```text
MISSING_NETWORK
PORT_BUSY_OR_BLOCKED
```

No image rebuild was performed in this commit because the required private
runtime stack inputs are not configured.
