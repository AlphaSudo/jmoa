# V2-C Historical Replay Results

Status: passed with one documented optional skip.

The replay used run-level evidence recovered from the local Phase 33 archive and
a synthetic invalid evidence fixture. Sanitized Markdown case-study summaries
were not used as replay input.

## Replay Summary

| Case | Evidence present | Expected | Actual | Result |
| --- | --- | --- | --- | --- |
| Phase 33M PetClinic exploded Boot full P2 | yes | CONFIRMED_WIN | CONFIRMED_WIN | pass |
| Phase 33K.7b PetClinic fat-JAR full P2 | yes | CONFIRMED_REGRESSION | CONFIRMED_REGRESSION | pass |
| Phase 32L Doctor corrected fat-JAR/CDS | no | CONFIRMED_WIN | skipped | optional raw evidence unavailable |
| Phase 32I Doctor invalid artifact fixture | yes | INVALID_EVIDENCE | INVALID_EVIDENCE | pass |

Replay totals:

```text
cases: 4
present cases: 3
passed cases: 3
skipped cases: 1
failed cases: 0
```

## Phase 33M Replay

The PetClinic exploded Boot full-P2 no-CDS confirmation replayed as a claimable
win.

```text
verdict: CONFIRMED_WIN
pairs: 3
paired wins: 3
median PSS delta: -4,758 KB
median Private_Dirty delta: -4,904 KB
median memory.current delta: -4,849,664 bytes
median heap PSS delta: -7,152 KB
```

This reproduces the audited Phase 33M decision: the public PetClinic no-CDS win
is valid under EXPLODED_BOOT_APP and NO_CDS_LOW_DIRTY policy.

## Phase 33K.7b Replay

The PetClinic fat-JAR full-P2 allocator-controlled confirmation replayed as a
regression, not a win.

```text
verdict: CONFIRMED_REGRESSION
pairs: 3
paired wins: 0
median PSS delta: +8,895 KB
median Private_Dirty delta: +9,072 KB
median memory.current delta: +9,392,128 bytes
median heap PSS delta: +8,748 KB
variance category: HEAP_PAGE_TOUCH
```

This reproduces the audited decision that full P2 must not be promoted in the
artificial fat-JAR launch shape.

## Invalid Evidence Replay

The invalid Doctor fixture was rejected for the expected hard-invalid reasons:

```text
workload errors > 0
health is not UP
missing post smaps_rollup
artifact hash mismatch
runtime policy mismatch
CDS mode mismatch
javaagent present while forbidden
missing or empty memory metrics
```

## Doctor 32L Status

Raw run-level Doctor 32L evidence was not found. The replay suite keeps this
case optional and reports it as skipped. The V2-C closure therefore does not
replay the Doctor corrected win; it documents that the old summary/audit files
were deliberately not used as substitute replay evidence.
