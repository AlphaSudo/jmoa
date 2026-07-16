# Patient No-CDS V2-D Attribution

Status: `PASSED`

V2-D ran only after V2-C accepted all six runs. Its role is explanation, not a
new optimization claim.

## Primary Attribution

`HEAP_PAGE_TOUCH_REDUCTION` with high confidence.

- Median heap PSS delta: `-5,300 KB`
- Median heap-used delta: `-335 KB`
- Median class-histogram bytes delta: `-262,464 B`

The large heap-PSS movement with nearly flat heap usage and histogram bytes is
not evidence of a material retained business-object reduction.

## Supporting Attribution

`ANONYMOUS_RW_ALLOCATOR_REDUCTION` with medium confidence.

- Median anonymous-writable PSS delta: `-3,328 KB`
- Median NMT total committed delta: `-3,986 KB`
- Median NMT-to-PSS gap: `-4,917 KB`
- NMT reconciliation: `NMT_INVISIBLE_OR_PARTIAL`

The result is therefore consistent with lower page touch and allocator/native
region movement under the explicit `MALLOC_ARENA_MAX=1` no-CDS protocol. It is
not a claim that the reducer always changes heap objects or class count.

## Other Signals

- Median class-histogram class-count delta: `+1`
- Median NMT metaspace committed delta: `-2,635 KB`
- Median NMT class committed delta: `0 KB`
- Median mapped-file PSS delta: `+44 KB`
- V2-A generated-family report: not joined to this private run report
- V2-B bytecode-runtime report: not joined to this private run report

V2-D explains this result as protocol-scoped memory movement. It does not
promote CDS, fat-JAR, startup, or cross-service claims.
