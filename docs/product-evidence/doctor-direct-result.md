# Doctor Direct Product Result

Comparison: clean no-JMOA `B0` to final JMOA V2.

| Pair | PSS | Private_Dirty | memory.current |
| ---: | ---: | ---: | ---: |
| 1 | -10,484 KB | -10,456 KB | -16,670,720 B |
| 2 | -5,809 KB | -5,492 KB | -11,845,632 B |
| 3 | -1,255 KB | -1,284 KB | -7,245,824 B |
| **Median** | **-5,809 KB** | **-5,492 KB** | **-11,845,632 B** |

All six runs were valid, all three pairs favored V2, and the 600-request
workload completed with zero errors in every run. The baseline and candidate
used corrected Spring Boot fat JARs and separately trained, artifact-specific
application CDS archives produced by the same 200-request training protocol.

V2-C returned `CONFIRMED_WIN`. V2-D identified a 131-class reduction and a
median 3,180 KB anonymous writable PSS reduction as supporting explanations.
The result clears the frozen substantial product gate.

The claim does not transfer to another Doctor artifact, a reused CDS archive,
or a different deployment/runtime policy.
