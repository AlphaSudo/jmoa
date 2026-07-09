# V2-K Doctor D2 Baseline Smoke

Status:

```text
PASSED
```

This smoke validates the corrected Doctor D2 baseline runtime used for the V2-K
runtime comparison.

Runtime scope:

```text
service: Doctor corrected D2
launch mode: SPRING_BOOT_FAT_JAR
runtime policy: CDS with a fresh current-runtime D2 archive
javaagent: absent
class-load logging: disabled during memory captures
JFR: disabled
NMT: summary
```

The older historical D2 CDS archive was not reused after the local Java runtime
reported a HotSpot archive-version mismatch. A fresh D2 CDS archive was trained
for the current Java 26 runtime.

Baseline smoke summary across the three confirmation baseline runs:

| Metric | Median |
| --- | ---: |
| Post-workload PSS | `327,175 KB` |
| Post-workload Private_Dirty | `295,732 KB` |
| Post-workload memory.current | `434,028,544 bytes` |
| Startup timing | `35.6 s` |

Workload:

```text
runs: 3
requests per run: 36
workload errors: 0
health: UP
secured Doctor endpoint: exercised
```

CDS archive:

```text
bytes: 128,237,568
sha256: 6FE999095F8800D3B820B87D60239EDD2217E730B5E75F9328737670EF6B8E3B
mapped at runtime: yes
```

Claim boundary:

```text
This proves the corrected D2 baseline is runnable and measurable in the local
Doctor runtime stack. It is not a reducer claim by itself.
```
