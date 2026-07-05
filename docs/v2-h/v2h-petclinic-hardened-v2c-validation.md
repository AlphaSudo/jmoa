# V2-H PetClinic Hardened V2-C Validation

V2-C validation was applied to the V2-H screen evidence shape.

## Screen Evidence Validity

```text
runs: 2
valid runs: 2
invalid runs: 0
health: UP
workload errors: 0
smaps_rollup present: true
full smaps present: true
memory.current present: true
NMT summary present: true
heap info present: true
class histogram present: true
CDS mode: off
javaagent present: false
MALLOC_ARENA_MAX: 1
class-load logging during memory run: false
```

## Confirmation Verdict

No V2-C paired confirmation verdict is issued because the single-screen promotion gate failed and the 3-pair confirmation was not run.

```text
screen evidence: valid
confirmation: not run
runtime claim: false
```

