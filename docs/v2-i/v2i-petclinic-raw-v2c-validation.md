# V2-I PetClinic Raw V2-C Validation

The flat Phase 33 runner output was transformed into the V2-C run-directory
archive shape:

```text
b1 b2 b3 c1 c2 c3
```

V2-C validation result:

```text
verdict: CONFIRMED_WIN
runs: 6
valid runs: 6
invalid runs: 0
pairs: 3
paired wins: 2
workload errors: 0
```

Required evidence was present:

```text
smaps_rollup: true
full smaps: true
memory.current: true
NMT summary: true
heap info: true
class histogram: true
CDS mode: off
javaagent present: false
class-load logging during memory run: false
```
