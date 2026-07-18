# Extended Confirmation

The public memory claim requires three valid balanced pairs after both artifacts
have passed materialization and semantic smoke.

## Required Inputs

- comparator and candidate launch scripts;
- frozen artifact paths and SHA-256 values;
- passed semantic-smoke reports;
- passed materialization proofs;
- corrected workload script and health endpoint;
- explicit launch mode and runtime policy;
- Podman runtime and permission to apply the declared cache policy.

`scripts/run-v2-confirmation.ps1` is the generic coordinator. It invokes
`runtime-screen-pair.ps1`, produces V2-C evidence, and then runs V2-D
attribution. Use `-PairOrder BALANCED` for `B -> C`, `C -> B`, `B -> C`.

The PetClinic-specific historical runner
`run-v2-final-customers-comparison.ps1` also depends on the pinned PetClinic
workspace and captured workload tooling. It is not a substitute for declaring
all generic inputs explicitly.

## Claim Gate

The final matrix requires `6/6` valid runs, at least `2/3` paired wins, median
PSS at or below `-4,096 KB`, median Private_Dirty at or below `-1,024 KB`,
median `memory.current` at or below `-1,048,576 B`, zero errors, V2-C
`CONFIRMED_WIN`, and V2-D attribution.

Do not enable class-load logging, JFR, NMT detail, forced GC, or extra diagnostic
output during official memory pairs. Capture those in separate diagnostic runs.

Raw evidence can contain machine paths and private runtime details. Keep it
under ignored `target/`; publish only reviewed sanitized summaries.
