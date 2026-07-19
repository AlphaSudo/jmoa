# Patient Direct Product Result

Comparison attempted: clean no-JMOA `B0` to candidate SHA `FB4E6295...`.

| Screen order | PSS | Private_Dirty | memory.current | Workload errors |
| --- | ---: | ---: | ---: | ---: |
| B0 then V2 | +1,146 KB | +1,364 KB | +1,245,184 B | 0 |
| V2 then B0 | +3,290 KB | +3,336 KB | +3,244,032 B | 0 |

Both arms mapped the identical stock Java 26 base CDS archive. Neither used a
Patient application archive or runtime javaagent, and both ran with
`MALLOC_ARENA_MAX=1`. Artifact and runtime hashes matched the supplied files.
The later lineage audit found that candidate `FB4E6295...` is not the accepted
corrected V2 artifact `4CFC40AD...`.

The first screen exposed different young-generation occupancy at capture time,
so the single allowed correction reversed run order without changing the JVM,
workload, cache, or capture protocol. The correction also regressed all three
primary metrics for `FB4E6295...`. Patient stopped before confirmation, and
these screens cannot decide the accepted V2 product result.

The historical Patient V1 and final V1-to-V2 results remain valid for their own
questions. They are not a direct no-JMOA-to-final-V2 claim.
