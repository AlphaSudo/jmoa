# V2-K Execution Plan

V2-K should advance one gate at a time.

## K1 Artifact Build

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

## K2 Artifact Smoke

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

## K3 Semantic Smoke

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

## K4 Runtime Screen

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

## K5 Confirmation

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
