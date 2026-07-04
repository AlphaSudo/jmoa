# V2-E PetClinic V2-C Validation

Status: valid and claimable under the configured policy.

V2-C analyzed the six confirmation runs for:

```text
expected policy: NO_CDS_LOW_DIRTY
artifact hashes required: true
workload zero errors required: true
smaps arithmetic required: true
```

Validation result:

```text
runs: 6
valid runs: 6
invalid runs: 0
pairs: 3
paired wins: 2
verdict: CONFIRMED_WIN
```

Runtime policy checks:

```text
CDS mode: OFF
javaagent present: false
class-load logging enabled: false
JFR enabled: false
NMT mode: summary
GC.run before capture: false
workload errors: 0
```

Variance note:

```text
CGROUP_MEMORY_CURRENT_DIVERGENCE
```

The memory.current median moved in the same favorable direction as PSS and
Private_Dirty, but its magnitude diverged enough from PSS for V2-C to flag the
cgroup metric as a variance category.

Runtime class-load logging was intentionally not enabled during memory pairs.
Materialization proof is tracked separately in
`v2e-petclinic-materialization-proof`.
