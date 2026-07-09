# V2-K Execution Plan

V2-K has two lanes:

```text
Lane A: Doctor runtime unblock
Lane B: public visits-service fallback / parallel portability
```

Doctor is the primary lane. Visits-service is the public fallback if Doctor
remains blocked.

Current Doctor result:

```text
BLOCKED_WITH_ROOT_CAUSE
```

The next executable lane is visits-service unless the missing Doctor runtime
pieces are restored.

## K1 Doctor Runtime Inventory

Create and maintain:

```text
scripts/doctor-runtime-inventory.ps1
scripts/doctor-runtime-unblock-gate.ps1
v2k-doctor-runtime-inventory.md/json
v2k-doctor-runtime-inventory-update.md/json
v2k-doctor-runtime-unblock-gate.md/json
```

Verdict categories:

```text
READY
MISSING_IMAGE
MISSING_CONFIG
MISSING_DATABASE
D2R_CDS_NOT_TRAINED
BLOCKED_PRIVATE_STACK
```

Doctor can move past inventory only when the runtime unblock gate no longer
reports private stack, image, network, CDS, or port blockers.

## K2 Doctor CDS Policy

Preferred:

```text
D2 + corrected D2 CDS
vs
D2R + freshly trained D2R CDS
```

Allowed diagnostic:

```text
D2 no-CDS
vs
D2R no-CDS
```

Forbidden:

```text
reuse old D2 CDS archive with raw-reduced D2R artifact
compare historical CDS baseline to no-CDS reduced candidate
```

## K3 Doctor Image Rebuild / Materialization Proof

Required before semantic smoke:

```text
Doctor baseline/candidate image exists
raw-reduced artifact hash known
BOOT-INF/lib hashes match reducer manifest
CDS archive hash matches candidate artifact if CDS is used
runtime command uses expected artifact
```

Outputs:

```text
v2k-doctor-image-rebuild.md/json
v2k-doctor-d2r-cds-training.md/json
v2k-doctor-runtime-materialization-proof.md/json
```

## K4 Doctor Semantic Smoke

Run only after K1-K3 are clean:

```text
health UP
database reachable
representative endpoints pass
0 workload errors
no VerifyError / ClassFormatError / linkage errors
```

Outputs:

```text
v2k-doctor-semantic-smoke.md/json
v2k-doctor-semantic-failure.md/json if failed
```

## K5 Doctor Runtime Screen

Run only after semantic smoke passes.

Valid comparisons:

```text
D2 + CDS vs D2R + freshly retrained D2R CDS
D2 no-CDS vs D2R no-CDS diagnostic
```

Invalid comparison:

```text
historical CDS D2 vs new no-CDS D2R
```

Outputs:

```text
v2k-doctor-runtime-screen.md/json
```

## K6 Doctor 3-Pair Confirmation

Only if Doctor screen passes:

```text
D2-1  -> D2R-1
D2-2  -> D2R-2
D2-3  -> D2R-3
```

Use V2-C and V2-D.

Outputs:

```text
v2k-doctor-confirmation.md/json
v2k-doctor-v2c-validation.md/json
v2k-doctor-v2d-attribution.md/json
v2k-doctor-final-verdict.md/json
```

## K7 Public Fallback: Visits-Service

Use visits-service if Doctor remains blocked or while the Doctor rebuild takes
too long.

## Visits K1 Artifact Build

Build:

```text
visits-service baseline artifact
visits-service raw-reduced artifact
```

If full P2 lambda optimization is available for visits-service, the primary
comparison should be:

```text
full P2
vs
full P2 + raw reducer
```

If full P2 is not ready for visits-service, the comparison must be stated as:

```text
service artifact
vs
service artifact + raw reducer
```

Do not mix those claims.

## Visits K2 Artifact Smoke

Run:

```text
jmoa.reducer.engine=raw
strip LocalVariableTable=true
strip LocalVariableTypeTable=true
signed/MR/sealed jars skipped
BootstrapMethods preserved
```

Gate:

```text
bytes removed > 0
0 failed raw byte-preservation audits
manifest v2 present
jar count stable
```

Outputs:

```text
v2k-visits-artifact-smoke.md/json
```

## Visits K3 Semantic Smoke

Run the service and verify:

```text
health UP
representative workload
0 workload errors
no VerifyError
no ClassFormatError
no linkage errors
```

If Docker/Podman or supporting services fail, write a blocked report.

Outputs:

```text
v2k-visits-semantic-smoke.md/json
v2k-visits-blocked-report.md/json if blocked
```

## Visits K4 Runtime Screen

Compare:

```text
target
vs
target + raw reducer
```

or:

```text
full P2
vs
full P2 + raw reducer
```

depending on what K1 built.

Capture:

```text
PSS
Private_Dirty
memory.current
heap PSS
anonymous_rw
NMT
heap used
class histogram
startup
workload result
run manifest
```

Promotion gate:

```text
artifact smaller
workload errors = 0
PSS not worse by > 1 MB
Private_Dirty not worse by > 1 MB
memory.current not worse by > 1 MB
```

## Visits K5 Confirmation

Only if the screen passes:

```text
T-1  -> TR-1
T-2  -> TR-2
T-3  -> TR-3
```

Use V2-C validation and V2-D attribution.

## Claim Rules

If confirmed:

```text
second public runtime confirmation for raw reducer
```

If screen fails:

```text
raw reducer remains PetClinic customers-service confirmed only
```

If blocked:

```text
no runtime claim
```

See:

```text
v2k-closure-outcomes.md/json
```
