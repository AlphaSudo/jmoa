# V2-L Visits V2-C Validation

Status:

```text
PASSED
```

V2-C analyzed six fresh visits-service runs in baseline/candidate pair order.

Validation:

```text
runs: 6
valid runs: 6
invalid runs: 0
runtime policy: NO_CDS_LOW_DIRTY
CDS mode: OFF
MALLOC_ARENA_MAX: 1
javaagent: absent
workload errors: 0
linkage errors: 0
smaps_rollup present: yes
full smaps present: yes
memory.current present: yes
NMT summary present: yes
heap info present: yes
class histogram present: yes
artifact hashes matched expected hashes: yes
runtime materialization proof present: yes
```

Perturbation controls:

```text
class-load logging: disabled
JFR: disabled
NMT detail: disabled
GC.run before capture: false
perturbation warnings: 0
```

Confirmation:

```text
verdict: CONFIRMED_WIN
pairs: 3
paired wins: 3
median PSS delta: -2,012 KB
median Private_Dirty delta: -1,680 KB
median memory.current delta: -1,712,128 bytes
median heap PSS delta: +1,988 KB
median anonymous_rw PSS delta: -5,264 KB
```

V2-C variance classification was `UNKNOWN`; no known variance rule matched
strongly enough. V2-D supplies the category-level explanation.
