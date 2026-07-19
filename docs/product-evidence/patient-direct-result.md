# Patient Direct Product Result

Comparison: clean no-JMOA `B0` to final JMOA V2.

| Screen order | PSS | Private_Dirty | memory.current | Workload errors |
| --- | ---: | ---: | ---: | ---: |
| B0 then V2 | +1,146 KB | +1,364 KB | +1,245,184 B | 0 |
| V2 then B0 | +3,290 KB | +3,336 KB | +3,244,032 B | 0 |

Both arms mapped the identical stock Java 26 base CDS archive. Neither used a
Patient application archive or runtime javaagent, and both ran with
`MALLOC_ARENA_MAX=1`. Artifact and runtime hashes matched the frozen clean B0
and final V2 JARs.

The first screen exposed different young-generation occupancy at capture time,
so the single allowed correction reversed run order without changing the JVM,
workload, cache, or capture protocol. The correction also regressed all three
primary metrics. Patient therefore stopped before confirmation.

The historical Patient V1 and final V1-to-V2 results remain valid for their own
questions. They are not a direct no-JMOA-to-final-V2 claim.
