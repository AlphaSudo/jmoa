# Patient Final Default-Base-CDS Verdict

Status: `PATIENT_BASE_CDS_V2_CONFIRMED`

This final experiment compared the accepted Patient V1 and corrected V2
artifacts under `JDK_BASE_CDS_LOW_DIRTY`. Both arms used the identical stock
JDK base archive, `MALLOC_ARENA_MAX=1`, and no Patient application archive,
AOT cache, or runtime javaagent.

## Qualification

- Default archive SHA-256: `77BCC7CC41CBD2EE8920CAE555019C722B4E40FCA731EAD484620D100FDC9EE6`
- Dependency JAR identities: `162/162` on both artifacts
- Raw preservation audit: `32616` classes, `0` failures
- Semantic workload: `600/600` requests per artifact, zero errors
- Live policy proof: `BASE_CDS_POLICY_PROOF_PASSED`

## Diagnostic Screen

- V2-minus-V1 PSS: `-12613 KB`
- V2-minus-V1 Private_Dirty: `-12564 KB`
- V2-minus-V1 memory.current: `-12894208 bytes`
- Decision: `PROMOTED_TO_CONFIRMATION`

## Confirmation

- Valid runs: `6/6`
- Paired wins: `3/3`
- Median V2-minus-V1 PSS: `-8279 KB`
- Median V2-minus-V1 Private_Dirty: `-8444 KB`
- Median V2-minus-V1 memory.current: `-8523776 bytes`
- V2-C: `CONFIRMED_WIN`
- V2-D passed: `True`
- Primary attribution: `HEAP_PAGE_TOUCH_REDUCTION`

## Terminal Policy

Patient now has two confirmed policies: `JDK_BASE_CDS_LOW_DIRTY` and `NO_CDS_LOW_DIRTY`. Dynamic application CDS remains blocked.

No five-pair rescue or further Patient V2 performance experiment is allowed.
This result does not change the permanent rejection of Patient dynamic
application archives and does not imply a universal CDS conclusion.
