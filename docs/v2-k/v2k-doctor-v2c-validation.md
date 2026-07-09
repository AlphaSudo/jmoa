# V2-K Doctor V2-C Validation

Status:

```text
PASSED
```

The recovered Doctor evidence was normalized into V2-C baseline/candidate run
folders and analyzed with the JMOA evidence engine.

V2-C validation:

```text
runs: 6
valid runs: 6
invalid runs: 0
runtime policy: CDS
CDS mode: ON
javaagent: absent
workload errors: 0
smaps_rollup present: yes
full smaps present: yes
memory.current present: yes
artifact hashes matched expected hashes: yes
runtime verification gate present: yes
```

V2-C confirmation:

```text
verdict: CONFIRMED_WIN
pairs: 3
paired wins: 3
median PSS delta: -5,156 KB
median Private_Dirty delta: -5,212 KB
median memory.current delta: -6,975,488 bytes
```

Variance classification:

```text
UNKNOWN
```

No known variance pattern matched strongly enough for V2-C alone. V2-D provides
the memory attribution.
