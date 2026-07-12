# V2-Q Visits Application Runtime Screen Rerun

The first V2-Q visits application-class screen failed its promotion gate. A
single clean diagnostic rerun was executed with the same comparison:

```text
dependency raw only
vs
dependency raw + admitted application raw
```

Runtime policy remained `EXPLODED_BOOT_APP`, `NO_CDS_LOW_DIRTY`,
`MALLOC_ARENA_MAX=1`, no CDS/AppCDS/Leyden, and no runtime javaagent.

## Result

| Metric | Delta |
| --- | ---: |
| PSS | `-6,852 KB` |
| Private_Dirty | `-7,064 KB` |
| memory.current | `-57,921,536 bytes` |
| Startup | `-6.3 s` |
| Workload errors | `0` |
| Linkage errors | `0` |

The diagnostic rerun passed the single-screen gate. Per V2-Q policy, this did
not create a runtime claim; it only allowed a fresh 3-pair confirmation attempt.
