# Patient Base-CDS Final Acceptance Contract

Status: `FROZEN`

This is the final Patient V2 performance experiment. It compares the accepted
Patient V1 and corrected V2 artifacts while both use only the identical default
JDK base CDS archive.

## Runtime Policy

Policy name: `JDK_BASE_CDS_LOW_DIRTY`

Both arms must use the same JDK vendor/build, `-Xshare:on`,
`MALLOC_ARENA_MAX=1`, identical JVM/container controls, and the same
600-request workload. The following are forbidden:

- `SharedArchiveFile` overrides;
- `ArchiveClassesAtExit`;
- Patient application archives;
- AOT cache/configuration flags;
- runtime javaagents;
- class-load logging, JFR, or diagnostic GC during memory pairs.

The live proof must identify the mapped default archive by path, SHA-256, and
device/inode, reject any additional `.jsa`, and prove identical archive identity
between V1 and V2.

## Frozen Gates

The one diagnostic screen promotes only when V2-minus-V1 is at most `-1,024 KB`
for PSS and Private_Dirty and at most `-1,048,576 bytes` for
`memory.current`, with zero workload and semantic/linkage errors.

If promoted, exactly three balanced pairs run in this order:

1. V1 then V2
2. V2 then V1
3. V1 then V2

Final success requires:

- `6/6` valid runs;
- at least `2/3` paired wins;
- median PSS delta at most `-4,096 KB`;
- median Private_Dirty delta at most `-1,024 KB`;
- median `memory.current` delta at most `-1,048,576 bytes`;
- zero workload and semantic/linkage errors;
- V2-C verdict `CONFIRMED_WIN`;
- V2-D attribution completed successfully.

No five-pair rescue, archive training, threshold change, or later Patient V2
experiment is permitted.

Terminal outcomes are `PATIENT_BASE_CDS_V2_CONFIRMED`,
`PATIENT_ALL_CDS_POLICIES_BLOCKED`, or
`PATIENT_BASE_CDS_ENVIRONMENT_INVALID`.
